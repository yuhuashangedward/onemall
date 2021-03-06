package cn.iocoder.mall.pay.biz.service;

import cn.iocoder.common.framework.util.DateUtil;
import cn.iocoder.common.framework.util.MathUtil;
import cn.iocoder.common.framework.util.ServiceExceptionUtil;
import cn.iocoder.common.framework.vo.CommonResult;
import cn.iocoder.mall.pay.api.PayTransactionService;
import cn.iocoder.mall.pay.api.bo.PayTransactionBO;
import cn.iocoder.mall.pay.api.bo.PayTransactionSubmitBO;
import cn.iocoder.mall.pay.api.constant.PayErrorCodeEnum;
import cn.iocoder.mall.pay.api.constant.PayTransactionStatusEnum;
import cn.iocoder.mall.pay.api.dto.PayTransactionCreateDTO;
import cn.iocoder.mall.pay.api.dto.PayTransactionSubmitDTO;
import cn.iocoder.mall.pay.biz.client.AbstractPaySDK;
import cn.iocoder.mall.pay.biz.client.PaySDKFactory;
import cn.iocoder.mall.pay.biz.client.TransactionSuccessBO;
import cn.iocoder.mall.pay.biz.convert.PayTransactionConvert;
import cn.iocoder.mall.pay.biz.dao.PayTransactionExtensionMapper;
import cn.iocoder.mall.pay.biz.dao.PayTransactionMapper;
import cn.iocoder.mall.pay.biz.dao.PayNotifyTaskMapper;
import cn.iocoder.mall.pay.biz.dataobject.PayAppDO;
import cn.iocoder.mall.pay.biz.dataobject.PayTransactionDO;
import cn.iocoder.mall.pay.biz.dataobject.PayTransactionExtensionDO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@org.apache.dubbo.config.annotation.Service(validation = "true")
public class PayTransactionServiceImpl implements PayTransactionService {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired
    private PayTransactionMapper payTransactionMapper;
    @Autowired
    private PayTransactionExtensionMapper payTransactionExtensionMapper;
    @Autowired
    private PayNotifyTaskMapper payTransactionNotifyTaskMapper;

    @Autowired
    private PayAppServiceImpl payAppService;
    @Autowired
    private PayNotifyServiceImpl payNotifyService;

    public PayTransactionDO getTransaction(Integer id) {
        return payTransactionMapper.selectById(id);
    }

    public PayTransactionDO getTransaction(String appId, String orderId) {
        return payTransactionMapper.selectByAppIdAndOrderId(appId, orderId);
    }

    public int updateTransactionPriceTotalIncr(Integer id, Integer incr) {
        return payTransactionMapper.updateForRefundTotal(id, incr);
    }

    public PayTransactionExtensionDO getPayTransactionExtension(Integer id) {
        return payTransactionExtensionMapper.selectById(id);
    }

    @Override
    public CommonResult<PayTransactionBO> getTransaction(Integer userId, String appId, String orderId) {
        PayTransactionDO payTransaction = payTransactionMapper.selectByAppIdAndOrderId(appId, orderId);
        if (payTransaction == null) {
            return ServiceExceptionUtil.error(PayErrorCodeEnum.PAY_TRANSACTION_NOT_FOUND.getCode());
        }
        // TODO 芋艿 userId 的校验
        return CommonResult.success(PayTransactionConvert.INSTANCE.convert(payTransaction));
    }

    @Override
    @SuppressWarnings("Duplicates")
    public CommonResult<PayTransactionBO> createTransaction(PayTransactionCreateDTO payTransactionCreateDTO) {
        // 校验 App
        CommonResult<PayAppDO> appResult = payAppService.validPayApp(payTransactionCreateDTO.getAppId());
        if (appResult.isError()) {
            return CommonResult.error(appResult);
        }
        // 插入 PayTransactionDO
        PayTransactionDO payTransaction = payTransactionMapper.selectByAppIdAndOrderId(
                payTransactionCreateDTO.getAppId(), payTransactionCreateDTO.getOrderId());
        if (payTransaction != null) {
            logger.warn("[createTransaction][appId({}) orderId({}) exists]", payTransactionCreateDTO.getAppId(),
                    payTransactionCreateDTO.getOrderId()); // 理论来说，不会出现这个情况
            // TODO 芋艿 可能要考虑，更新订单。例如说，业务线订单可以修改价格
        } else {
            payTransaction = PayTransactionConvert.INSTANCE.convert(payTransactionCreateDTO);
            payTransaction.setStatus(PayTransactionStatusEnum.WAITING.getValue())
                    .setNotifyUrl(appResult.getData().getNotifyUrl());
            payTransaction.setCreateTime(new Date());
            payTransactionMapper.insert(payTransaction);
        }
        // 返回成功
        return CommonResult.success(PayTransactionConvert.INSTANCE.convert(payTransaction));
    }

    @Override
    @SuppressWarnings("Duplicates")
    public CommonResult<PayTransactionSubmitBO> submitTransaction(PayTransactionSubmitDTO payTransactionSubmitDTO) {
        // TODO 校验支付渠道是否有效
        // 校验 App 是否有效
        CommonResult<PayAppDO> appResult = payAppService.validPayApp(payTransactionSubmitDTO.getAppId());
        if (appResult.isError()) {
            return CommonResult.error(appResult);
        }
        // 获得 PayTransactionDO ，并校验其是否存在
        PayTransactionDO payTransaction = payTransactionMapper.selectByAppIdAndOrderId(
                payTransactionSubmitDTO.getAppId(), payTransactionSubmitDTO.getOrderId());
        if (payTransaction == null) { // 是否存在
            return ServiceExceptionUtil.error(PayErrorCodeEnum.PAY_TRANSACTION_NOT_FOUND.getCode());
        }
        if (!PayTransactionStatusEnum.WAITING.getValue().equals(payTransaction.getStatus())) { // 校验状态，必须是待支付
            return ServiceExceptionUtil.error(PayErrorCodeEnum.PAY_TRANSACTION_STATUS_IS_NOT_WAITING.getCode());
        }
        // 插入 PayTransactionExtensionDO
        PayTransactionExtensionDO payTransactionExtensionDO = PayTransactionConvert.INSTANCE.convert(payTransactionSubmitDTO)
                .setTransactionId(payTransaction.getId())
                .setTransactionCode(generateTransactionCode())
                .setStatus(PayTransactionStatusEnum.WAITING.getValue());
        payTransactionExtensionMapper.insert(payTransactionExtensionDO);
        // 调用三方接口
        AbstractPaySDK paySDK = PaySDKFactory.getSDK(payTransactionSubmitDTO.getPayChannel());
        CommonResult<String> invokeResult = paySDK.submitTransaction(payTransaction, payTransactionExtensionDO, null); // TODO 暂时传入 extra = null
        if (invokeResult.isError()) {
            return CommonResult.error(invokeResult);
        }
        // TODO 轮询三方接口，是否已经支付的任务
        // 返回成功
        PayTransactionSubmitBO payTransactionSubmitBO = new PayTransactionSubmitBO()
                .setId(payTransactionExtensionDO.getId()).setInvokeResponse(invokeResult.getData());
        return CommonResult.success(payTransactionSubmitBO);
    }

    @Override
    @Transactional
    public CommonResult<Boolean> updateTransactionPaySuccess(Integer payChannel, String params) {
        // TODO 芋艿，记录回调日志
        // 解析传入的参数，成 TransactionSuccessBO 对象
        AbstractPaySDK paySDK = PaySDKFactory.getSDK(payChannel);
        CommonResult<TransactionSuccessBO> paySuccessResult = paySDK.parseTransactionSuccessParams(params);
        if (paySuccessResult.isError()) {
            return CommonResult.error(paySuccessResult);
        }
        // TODO 芋艿，先最严格的校验。即使调用方重复调用，实际哪个订单已经被重复回调的支付，也返回 false 。也没问题，因为实际已经回调成功了。
        // 1.1 查询 PayTransactionExtensionDO
        PayTransactionExtensionDO extension = payTransactionExtensionMapper.selectByTransactionCode(paySuccessResult.getData().getTransactionCode());
        if (extension == null) {
            return ServiceExceptionUtil.error(PayErrorCodeEnum.PAY_TRANSACTION_EXTENSION_NOT_FOUND.getCode());
        }
        if (!PayTransactionStatusEnum.WAITING.getValue().equals(extension.getStatus())) { // 校验状态，必须是待支付
            return ServiceExceptionUtil.error(PayErrorCodeEnum.PAY_TRANSACTION_EXTENSION_STATUS_IS_NOT_WAITING.getCode());
        }
        // 1.2 更新 PayTransactionExtensionDO
        PayTransactionExtensionDO updatePayTransactionExtension = new PayTransactionExtensionDO()
                .setId(extension.getId())
                .setStatus(PayTransactionStatusEnum.SUCCESS.getValue())
                .setExtensionData(params);
        int updateCounts = payTransactionExtensionMapper.update(updatePayTransactionExtension, PayTransactionStatusEnum.WAITING.getValue());
        if (updateCounts == 0) { // 校验状态，必须是待支付
            throw ServiceExceptionUtil.exception(PayErrorCodeEnum.PAY_TRANSACTION_EXTENSION_STATUS_IS_NOT_WAITING.getCode());
        }
        logger.info("[updateTransactionPaySuccess][PayTransactionExtensionDO({}) 更新为已支付]", extension.getId());
        // 2.1 判断 PayTransactionDO 是否处于待支付
        PayTransactionDO transaction = payTransactionMapper.selectById(extension.getTransactionId());
        if (transaction == null) {
            return ServiceExceptionUtil.error(PayErrorCodeEnum.PAY_TRANSACTION_NOT_FOUND.getCode());
        }
        if (!PayTransactionStatusEnum.WAITING.getValue().equals(transaction.getStatus())) { // 校验状态，必须是待支付
            throw ServiceExceptionUtil.exception(PayErrorCodeEnum.PAY_TRANSACTION_STATUS_IS_NOT_WAITING.getCode());
        }
        // 2.2 更新 PayTransactionDO
        PayTransactionDO updatePayTransaction = new PayTransactionDO()
                .setId(transaction.getId())
                .setStatus(PayTransactionStatusEnum.SUCCESS.getValue())
                .setExtensionId(extension.getId())
                .setPayChannel(payChannel)
                .setPaymentTime(paySuccessResult.getData().getPaymentTime())
                .setNotifyTime(new Date())
                .setTradeNo(paySuccessResult.getData().getTradeNo());
        updateCounts = payTransactionMapper.update(updatePayTransaction, PayTransactionStatusEnum.WAITING.getValue());
        if (updateCounts == 0) { // 校验状态，必须是待支付 TODO 这种类型，需要思考下。需要返回错误，但是又要保证事务回滚
            throw ServiceExceptionUtil.exception(PayErrorCodeEnum.PAY_TRANSACTION_STATUS_IS_NOT_WAITING.getCode());
        }
        logger.info("[updateTransactionPaySuccess][PayTransactionDO({}) 更新为已支付]", transaction.getId());
        // 3 新增 PayNotifyTaskDO
        payNotifyService.addTransactionNotifyTask(transaction, extension);
        // 返回结果
        return CommonResult.success(true);
    }

    @Override // TODO 芋艿，后面去实现
    public CommonResult cancelTransaction() {
        return null;
    }

    private String generateTransactionCode() {
//    wx
//    2014
//    10
//    27
//    20
//    09
//    39
//    5522657
//    a690389285100
        // 目前的算法
        // 时间序列，年月日时分秒 14 位
        // 纯随机，6 位 TODO 此处估计是会有问题的，后续在调整
        return DateUtil.format(new Date(), "yyyyMMddHHmmss") + // 时间序列
                MathUtil.random(100000, 999999) // 随机。为什么是这个范围，因为偷懒
                ;
    }


}
