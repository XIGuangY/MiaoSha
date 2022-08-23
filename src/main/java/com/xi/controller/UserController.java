package com.xi.controller;


import com.alibaba.druid.util.StringUtils;
import com.xi.controller.viewobject.UserVO;
import com.xi.error.BusinessException;
import com.xi.error.CommonError;
import com.xi.error.EmBussinessError;
import com.xi.response.CommonReturnType;
import com.xi.service.UserService;
import com.xi.service.model.UserModel;
import jdk.net.SocketFlow;
import org.apache.tomcat.util.security.MD5Encoder;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;


import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Controller("user")
@RequestMapping("/user")
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")
public class UserController extends BaseController{
    @Autowired
    private UserService userService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @RequestMapping(value = "/getotp",method = {RequestMethod.POST},consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType getOpt(@RequestParam(name = "telphone") String telphone){
        Random random = new Random();
        int randomInt = random.nextInt(99999);
        randomInt +=10000;
        String otpCode = String.valueOf(randomInt);

        // 两个变量绑定
        httpServletRequest.getSession().setAttribute(telphone, otpCode);


        Date day=new Date();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        System.out.println("telphone:" + telphone  + " | otpCode:" + otpCode + "   "+df.format(day));

        return CommonReturnType.creat(null);

    }



    @RequestMapping("/get")
    @ResponseBody
    public CommonReturnType getUser(@RequestParam(name = "id")Integer id) throws BusinessException {
        // 调用service服务，获取对应id的用户对象
        UserModel userModel = userService.getUserById(id);

        if(userModel==null){
            throw new BusinessException(EmBussinessError.USER_NOT_EXIST);
//            userModel.setEncrptPassword("123");
        }

        UserVO userVO = convertFromUserDO(userModel);



        return CommonReturnType.creat(userVO);
    }
    public UserVO convertFromUserDO(UserModel userModel){
        if(userModel==null){
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userModel, userVO);
        return userVO;
    }


    @RequestMapping(value = "register", method = {RequestMethod.POST} ,consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType register(@RequestParam(name = "telphone") String telphone,
                                     @RequestParam(name = "otpCode") String otpCode,
                                     @RequestParam(name = "name") String name,
                                     @RequestParam(name = "gender") String gender,
                                     @RequestParam(name = "age") String age,
                                     @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        String inSessionOtpCode = (String) this.httpServletRequest.getSession().getAttribute(telphone);

//        if(! com.alibaba.druid.util.StringUtils.equals(otpCode, inSessionOtpCode)){
//            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, "错误的短信验证码");
//        }
        if(! otpCode.equals(inSessionOtpCode)){
            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, "错误的短信验证码");
        }
        UserModel userModel = new UserModel();
        userModel.setName(name);
        userModel.setAge(Integer.valueOf(age));
        userModel.setGender(Byte.valueOf(gender));
        userModel.setTelphone(telphone);
        userModel.setRegisterMode("phone");
        userModel.setEncrptPassword(EncodeByMD5(password));
        userService.redister(userModel);
        return CommonReturnType.creat(userModel);
    }
    private String EncodeByMD5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        BASE64Encoder base64Encoder = new BASE64Encoder();
        String newstr = base64Encoder.encode(md5.digest(str.getBytes("utf-8")));
        return newstr;
    }

    @RequestMapping(value = "login", method = {RequestMethod.POST}, consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType login(@RequestParam(name = "telphone") String telphone,
                                  @RequestParam(name = "password") String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {

        if(StringUtils.isEmpty(telphone) || StringUtils.isEmpty(password)){
            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR);
        }

        UserModel userModel = userService.validateLogin(telphone, this.EncodeByMD5(password));



        //生成登录凭证tokenid
        String uuidToken = UUID.randomUUID().toString();
        uuidToken = uuidToken.replace("-", "");
        //建立token与用户登录态的联系
        redisTemplate.opsForValue().set(uuidToken,userModel);
        redisTemplate.expire(uuidToken, 1, TimeUnit.HOURS);
//        this.httpServletRequest.getSession().setAttribute("IS_LOGIN", true);
//        this.httpServletRequest.getSession().setAttribute("LOGIN_USER", userModel);

        //下发token
        return CommonReturnType.creat(uuidToken);

    }




}
