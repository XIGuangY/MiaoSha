package com.xi.service;

import com.xi.error.BusinessException;
import com.xi.service.model.UserModel;

public interface UserService {

    UserModel getUserById(Integer id) throws BusinessException;

    //通过缓存获取用户对象
    UserModel getUserByIdInCache(Integer id);


    void redister(UserModel userModel) throws BusinessException;
    UserModel validateLogin(String telphone, String password) throws BusinessException;
}
