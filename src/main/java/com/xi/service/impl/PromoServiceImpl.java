package com.xi.service.impl;

import com.xi.dao.PromoDOMapper;
import com.xi.dataobject.PromoDO;
import com.xi.error.BusinessException;
import com.xi.error.EmBussinessError;
import com.xi.service.ItemService;
import com.xi.service.PromoService;
import com.xi.service.UserService;
import com.xi.service.model.ItemModel;
import com.xi.service.model.PromoModel;
import com.xi.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromoServiceImpl implements PromoService {
    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;
    @Autowired
    private RedisTemplate redisTemplate;
    @Autowired
    private UserService userService;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        PromoModel promoModel = convertPromoModelFromPromoDO(promoDO);
        if(promoModel == null){
            return null;
        }

//        1 未开始   2 正在进行  3 已结束

        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else {
            promoModel.setStatus(2);
        }
        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        // 通过活动id 获取活动
        PromoDO promoDO  = promoDOMapper.selectByPrimaryKey(promoId);
        if(promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0){
            return;
        }
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());

        //将库存同步到redis内
        redisTemplate.opsForValue().set("promo_item_stock_"+ itemModel.getId(), itemModel.getStock());


        // 将大闸的限制数字设到redis内
        redisTemplate.opsForValue().set("promo_door_count_"+promoId, itemModel.getStock().intValue() * 5);

    }

    @Override
    public String generateSecondKillToken(Integer promoId, Integer itemId, Integer userId) {
        //判断库存是否售罄
        if(redisTemplate.hasKey("promo_item_stock_invalid_" + itemId)){
            return null;
        }
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        PromoModel promoModel = convertPromoModelFromPromoDO(promoDO);
        if(promoModel == null){
            return null;
        }
//        1 未开始   2 正在进行  3 已结束
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else {
            promoModel.setStatus(2);
        }
        //判断是否是秒杀商品
        if(promoModel.getStatus() !=2){
            return null;
        }
        // 判断商品是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel == null){
            return null;
        }

        //判断用户是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel == null ){
            return null;
        }
        // 获取秒杀大闸的数量
        long result = redisTemplate.opsForValue().increment("promo_door_count_"+promoId, -1);
        if(result<0){
            return null;
        }
        // 生成令牌
        String token = UUID.randomUUID().toString().replace("-","");
        redisTemplate.opsForValue().set("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId, token);
        redisTemplate.expire("promo_token_"+promoId+"_userid_"+userId+"_itemid_"+itemId,5, TimeUnit.MINUTES);

        return token;

    }

    private PromoModel convertPromoModelFromPromoDO(PromoDO promoDO){
        if(promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO , promoModel);
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        return promoModel;
    }
}
