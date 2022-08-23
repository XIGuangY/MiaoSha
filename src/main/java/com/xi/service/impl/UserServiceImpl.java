package com.xi.service.impl;

import com.alibaba.druid.util.StringUtils;
import com.xi.Validator.ValidationResult;
import com.xi.Validator.ValidatorImpl;
import com.xi.dao.UserDOMapper;
import com.xi.dao.UserPasswordDOMapper;
import com.xi.dataobject.UserDO;
import com.xi.dataobject.UserPasswordDO;
import com.xi.error.BusinessException;
import com.xi.error.EmBussinessError;
import com.xi.service.UserService;
import com.xi.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.beans.Transient;
import java.util.concurrent.TimeUnit;
import java.util.zip.DataFormatException;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserDOMapper userDOMapper;

    @Autowired
    private UserPasswordDOMapper userPasswordDOMapper;

    @Autowired
    ValidatorImpl validator;

    @Autowired
    private RedisTemplate redisTemplate;





    @Override
    public UserModel getUserById(Integer id) throws BusinessException {
        //调用mapper
        UserDO userDO = userDOMapper.selectByPrimaryKey(id);
        if(userDO==null) return null;

        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel = convertFromDataObject(userDO, userPasswordDO);

//        优化校验规则
//        ValidationResult result = validator.validate(userModel);
//        if(result.isHasErrors()){
//            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, result.getErrMsg());
//        }
        return userModel;
    }

    @Override
    public UserModel getUserByIdInCache(Integer id) {
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get("user_validate_"+id);
        if(userModel == null){
            try {
                userModel = this.getUserById(id);
            } catch (BusinessException e) {
                e.printStackTrace();
            }
            redisTemplate.opsForValue().set("user_validate_"+id, userModel);
            redisTemplate.expire("user_validate_"+id, 10 , TimeUnit.MINUTES);
        }
        return userModel;
    }

    @Override
    @Transactional
    public void redister(UserModel userModel) throws BusinessException {
        if(userModel == null){
            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR);
        }
        if(StringUtils.isEmpty(userModel.getName())
                || userModel.getAge() == null
                || userModel.getGender() == null
                || StringUtils.isEmpty(userModel.getTelphone())
                || StringUtils.isEmpty(userModel.getEncrptPassword())) {
            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR);
        }

        UserDO userDO = convertFromUserModel(userModel);

        try {
            userDOMapper.insertSelective(userDO);
        }catch (DuplicateKeyException ex){
            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, "手机号已被注册！");
        }

        userModel.setId(userDO.getId());
        UserPasswordDO userPasswordDO = convertPasswordFromUserModel(userModel);
        userPasswordDOMapper.insertSelective(userPasswordDO);
        return;
    }

    @Override
    public UserModel validateLogin(String telphone, String password) throws BusinessException {
        UserDO userDO = userDOMapper.selectByTelphone(telphone);
        if(userDO == null){
            throw new BusinessException(EmBussinessError.USER_NOT_EXIST);
        }

        UserPasswordDO userPasswordDO = userPasswordDOMapper.selectByUserId(userDO.getId());
        UserModel userModel = convertFromDataObject(userDO, userPasswordDO);

        if(!StringUtils.equals(userModel.getEncrptPassword(), password)){
            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, "用户密码输入错误！");
        }
        return userModel;
    }




    private UserPasswordDO convertPasswordFromUserModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserPasswordDO userPasswordDO = new UserPasswordDO();
        userPasswordDO.setEncrptPassword(userModel.getEncrptPassword());
        userPasswordDO.setUserId(userModel.getId());
        return userPasswordDO;
    }
    private UserDO convertFromUserModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserDO userDO = new UserDO();
        BeanUtils.copyProperties(userModel, userDO);
        return userDO;

    }


    private UserModel convertFromDataObject(UserDO userDO, UserPasswordDO userPasswordDO){
        if(userDO == null){
            return null;
        }
        UserModel userModel = new UserModel();
        BeanUtils.copyProperties(userDO, userModel);
        if(userPasswordDO!=null){
            userModel.setEncrptPassword(userPasswordDO.getEncrptPassword());
        }

        return userModel;


    }

}
