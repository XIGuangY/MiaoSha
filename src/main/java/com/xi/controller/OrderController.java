package com.xi.controller;


import com.alibaba.druid.util.StringUtils;
import com.google.common.util.concurrent.RateLimiter;
import com.xi.error.BusinessException;
import com.xi.error.EmBussinessError;
import com.xi.mq.MqProducer;
import com.xi.response.CommonReturnType;
import com.xi.service.ItemService;
import com.xi.service.OrderService;
import com.xi.service.PromoService;
import com.xi.service.model.OrderModel;
import com.xi.service.model.UserModel;
import org.apache.ibatis.annotations.Param;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.*;

@Controller("order")
@RequestMapping("/order")
@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")
public class OrderController extends BaseController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private HttpServletRequest httpServletRequest;
    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;
    @Autowired
    private ItemService itemService;
    @Autowired
    private PromoService promoService;

    private ExecutorService executorService;

    private RateLimiter orderCreateRateLimiter;


    @PostConstruct
    public void init(){
        executorService = Executors.newFixedThreadPool(20);

        orderCreateRateLimiter = RateLimiter.create(300);
    }


    // 获取令牌
    @RequestMapping(value = "/generateorder", method = {RequestMethod.POST},consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType generateorder(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "promoId") Integer promoId ) throws BusinessException {
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBussinessError.USER_NOT_LOGIN,"用户尚未登录，不能下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBussinessError.USER_NOT_LOGIN,"用户尚未登录，不能下单");
        }



        //获取秒杀令牌
        String promoToken = promoService.generateSecondKillToken(promoId, itemId, userModel.getId());

        if(promoToken == null){
            throw new BusinessException(EmBussinessError.UNKNOWN_ERROR, "生成令牌失败");
        }

        return CommonReturnType.creat(promoToken);

    }


    @RequestMapping(value = "/createorder", method = {RequestMethod.POST},consumes = {"application/x-www-form-urlencoded"})
    @ResponseBody
    public CommonReturnType createorder(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "amount") Integer amount,
                                        @RequestParam(name = "promoId", required = false) Integer promoId,
                                        @RequestParam(name = "promoToken", required = false) String promoToken) throws BusinessException {

        if(!orderCreateRateLimiter.tryAcquire()){
            throw new BusinessException(EmBussinessError.UNKNOWN_ERROR,"活动火爆，请稍后再试");
        }


        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBussinessError.USER_NOT_LOGIN,"用户尚未登录，不能下单");
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBussinessError.USER_NOT_LOGIN,"用户尚未登录，不能下单");
        }


        //检验令牌
        if(promoId != null){
            String inRedisPromoToken = (String) redisTemplate.opsForValue().get("promo_token_"+promoId+"_userid_"+userModel.getId()+"_itemid_"+itemId);
            if(inRedisPromoToken == null){
                throw new BusinessException(EmBussinessError.UNKNOWN_ERROR, "秒杀令牌校验失败");
            }
            if(!org.apache.commons.lang3.StringUtils.equals(promoToken, inRedisPromoToken)){
                throw new BusinessException(EmBussinessError.UNKNOWN_ERROR, "秒杀令牌校验失败");
            }
        }



        //同步调用线程池的submit方法
        // 拥塞窗口为20的等待队列，用来队列泄洪
        Future<Object> future =  executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //加入库存流水init状态
                String stockLogId = itemService.initStockLog(itemId, amount);
                //完成下单事务型消息
                if(!mqProducer.transactionAsyncReduceStock(userModel.getId(), itemId, promoId, amount, stockLogId)){
                    throw new BusinessException(EmBussinessError.USER_NOT_LOGIN, "下单失败，事务提交时");
                }
                return null;
            }
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EmBussinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            e.printStackTrace();
        }


        return CommonReturnType.creat(null);
    }


}
