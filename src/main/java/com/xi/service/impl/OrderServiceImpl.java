package com.xi.service.impl;

import com.sun.tools.corba.se.idl.constExpr.Or;
import com.xi.dao.OrderDOMapper;
import com.xi.dao.SequenceDOMapper;
import com.xi.dao.StockLogDOMapper;
import com.xi.dataobject.OrderDO;
import com.xi.dataobject.SequenceDO;
import com.xi.dataobject.StockLogDO;
import com.xi.error.BusinessException;
import com.xi.error.EmBussinessError;
import com.xi.service.ItemService;
import com.xi.service.OrderService;
import com.xi.service.UserService;
import com.xi.service.model.ItemModel;
import com.xi.service.model.OrderModel;
import com.xi.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private ItemService itemService;
    @Autowired
    private UserService userService;
    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    StockLogDOMapper stockLogDOMapper;

    @Override
    @Transactional
    public OrderModel createOrder(Integer userId, Integer itemId,Integer promoId, Integer amount, String stockLogId) throws BusinessException {

        //1.校验所要商品是否存在
//        ItemModel itemModel = itemService.getItemById(itemId);
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, "商品信息不存在");
        }
////        UserModel userModel = userService.getUserById(userId);
//        UserModel userModel = userService.getUserByIdInCache(userId);
//        if(userModel == null ){
//            throw  new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, "用户信息不存在");
//        }
        if(amount<=0 || amount>99){
            throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, "数量信息不正确");
        }
        // 检验活动信息
//        if(promoId != null){
//            //1、检验对应活动是否存在这个适用商品
//            if(promoId.intValue() != itemModel.getPromoModel().getId()){
//                throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, "活动信息不存在");
//            }else if(itemModel.getPromoModel().getStatus() !=2){
//                throw new BusinessException(EmBussinessError.PARAMETER_VALIDATION_ERROR, "活动不存在");
//            }
//        }


        //2.落单减库存
        boolean tradeResult = itemService.decreaseStock(itemId, amount);
        if(!tradeResult){
            throw new BusinessException(EmBussinessError.TRADE_ERROR);
        }
        //3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemId);
        orderModel.setAmount(amount);
        if(promoId != null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else {
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));
        //生成交易流水号，订单号
        orderModel.setId(generatorOrderSNo());
        OrderDO orderDO = convertOrderDOFromOrderModel(orderModel);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        orderDOMapper.insertSelective(orderDO);

        itemService.increaseSales(itemId, amount);

        //设置库存流水状态为成功
        StockLogDO stockLogDO = stockLogDOMapper.selectByPrimaryKey(stockLogId);
        if(stockLogDO == null){
            throw new BusinessException(EmBussinessError.UNKNOWN_ERROR,"数据库获取库存流水失败");
        }
        stockLogDO.setStatus(2);
        stockLogDOMapper.updateByPrimaryKeySelective(stockLogDO);


//        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
//
//            @Override
//            public void afterCommit(){
//                //异步更新库存
//                boolean mqResult = itemService.asycnDecreaseStock(itemId, amount);
////                if(!mqResult){
////                    itemService.increaseStock(itemId,amount);
////                    throw new BusinessException(EmBussinessError.MQ_SEND_FAIL);
////                }
//            }
//
//        });
        //4.返回前端
        return orderModel;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    String generatorOrderSNo(){
        //订单16位
        //前8位时间信息， 年月日
        StringBuilder stringBuilder = new StringBuilder();
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-", "");
        stringBuilder.append(nowDate);
        //中间6位自增序列。新建一个记录递增数的数据库表，不断的更新数据库表
        SequenceDO sequenceDO = sequenceDOMapper.getSequenceByName("order_info");
        int sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        String sequenceStr = String.valueOf(sequence);
        for (int i = 0; i < 6 - sequenceStr.length(); i++) {
            stringBuilder.append("0");
        }
        stringBuilder.append(sequenceStr);
        //最后2位为分库分表位,暂时写死
        stringBuilder.append("00");
        return stringBuilder.toString();
    }


    private OrderDO convertOrderDOFromOrderModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel, orderDO);
        return orderDO;
    }
}
