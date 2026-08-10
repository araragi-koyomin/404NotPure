package com.example.tomatomall.controller;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.example.tomatomall.dto.PaymentData;
import com.example.tomatomall.exception.TomatoException;
import com.example.tomatomall.po.AliPay;
//import com.xdong.shopping.dao.pojo.ShoppingDd;
import com.example.tomatomall.po.Orders;
import com.example.tomatomall.repository.OrdersRepository;
import com.example.tomatomall.service.serviceImpl.PaymentService;
import com.example.tomatomall.vo.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 支付宝支付控制器
 *
 * 该控制器负责处理与支付宝支付相关的所有操作，包括：
 * 1. 生成支付表单
 * 2. 处理支付宝异步通知
 * 3. 处理支付完成后的同步跳转
 *
 * 使用@RestController注解表示这是一个RESTful风格的控制器
 * 使用@RequestMapping注解定义基础路径为"/api/orders"
 */
@RestController
@RequestMapping("/api/orders")
public class AliPayController {
  // 从配置文件中注入
  @Value("${alipay.app-id}")
  private String appId;
  @Value("${alipay.seller-id:}")
  private String sellerId;
  @Value("${alipay.private-key}")
  private String privateKey;
  @Value("${alipay.alipay-public-key}")
  private String alipayPublicKey;
  @Value("${alipay.server-url}")
  private String serverUrl;
  @Value("${alipay.charset}")
  private String charset;
  @Value("${alipay.sign-type}")
  private String signType;
  @Value("${alipay.notify-url}")
  private String notifyUrl;
  @Value("${alipay.return-url}")
  private String returnUrl;
  @Value("${alipay.frontEnd-url}")
  private String frontEndUrl;

  // 订单仓库，用于操作订单数据
  @Autowired
  private OrdersRepository ordersRepository;

  // 支付服务，用于处理支付相关业务逻辑
  @Autowired
  private PaymentService paymentService;

  /**
   * 创建支付表单接口（POST方式）
   *
   * 根据订单ID生成支付宝支付表单
   *
   * @param orderId 订单ID
   * @param response HTTP响应对象
   * @return 返回包含支付表单数据的响应对象
   * @throws IOException 可能抛出IO异常
   * @throws AlipayApiException 可能抛出支付宝API异常
   */
  @PostMapping("/{orderId}/pay")
  public Response<PaymentData> createPaymentForm(
      @PathVariable Integer orderId,
      HttpServletResponse response
  ) throws AlipayApiException {
    // 1. 验证订单存在且状态为待支付
    Orders order = ordersRepository.findById(orderId)
        .orElseThrow(() -> new RuntimeException("订单不存在"));
    if (!"PENDING".equals(order.getStatus())) {
      throw new RuntimeException("订单状态异常");
    }

    // 2. 构建支付宝客户端和请求
    AlipayClient alipayClient = new DefaultAlipayClient(serverUrl, appId, privateKey, "json", "UTF-8", alipayPublicKey, "RSA2");
    AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
    request.setNotifyUrl(notifyUrl);
    request.setReturnUrl(returnUrl);

    // 3. 设置支付参数
    JSONObject bizContent = new JSONObject();
    bizContent.put("out_trade_no", orderId.toString());
    bizContent.put("total_amount", order.getTotalAmount().toString());
    bizContent.put("subject", "订单支付 - " + orderId);
    bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
    request.setBizContent(bizContent.toJSONString());

    // 4. 生成支付表单
    String form = alipayClient.pageExecute(request).getBody();

    // 5. 构建响应数据
    PaymentData paymentData = new PaymentData();
    paymentData.setPaymentForm(form);
    paymentData.setOrderId(orderId.toString());
    paymentData.setTotalAmount(order.getTotalAmount().toString());
    paymentData.setPaymentMethod("Alipay");

    return Response.buildSuccess(paymentData);
  }

  /**
   * 支付宝异步通知接口
   *
   * 处理支付宝服务器发送的支付结果异步通知
   *
   * @param request HTTP请求对象，包含支付宝通知参数
   * @param response HTTP响应对象，用于返回处理结果
   */
  @PostMapping("/notify")
  public void handleAlipayNotify(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // 1. 将请求参数转换为Map
    Map<String, String> params = request.getParameterMap().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()[0]));

    // 2. 验证支付宝签名
    try {
      boolean signVerified = AlipaySignature.rsaCheckV1(params, alipayPublicKey, charset, signType);
      if (!signVerified) {
        response.getWriter().print("fail");  // 签名验证失败
        return;
      }
    } catch (AlipayApiException e) {
      response.getWriter().print("fail");
      return;
    }

    // 3. 处理交易成功逻辑
    if (!matchesNotificationRecipient(params)) {
      response.getWriter().print("fail");
      return;
    }

    if (isSuccessfulTradeStatus(params.get("trade_status"))) {
      String orderId = params.get("out_trade_no");  // 商户订单号
      String alipayTradeNo = params.get("trade_no");  // 支付宝交易号
      String totalAmount = params.get("total_amount");  // 交易金额

      try {
        // 更新订单状态
        paymentService.updateOrderStatus(orderId, alipayTradeNo, totalAmount);
      } catch (Exception e) {
        response.getWriter().print("fail");
        return;
      }
    } else {
      response.getWriter().print("fail");
      return;
    }

    // 4. 返回处理成功标识
    response.getWriter().print("success");
  }

  private boolean matchesNotificationRecipient(Map<String, String> params) {
    return appId != null && !appId.trim().isEmpty()
        && sellerId != null && !sellerId.trim().isEmpty()
        && appId.equals(params.get("app_id"))
        && sellerId.equals(params.get("seller_id"));
  }

  private boolean isSuccessfulTradeStatus(String tradeStatus) {
    return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus);
  }

  /**
   * 支付宝同步跳转接口
   *
   * 处理用户支付完成后的同步跳转
   *
   * @param request HTTP请求对象
   * @param response HTTP响应对象
   */
  @GetMapping("/returnUrl")
  public void returnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // 1. 获取订单号
    String orderId = request.getParameter("out_trade_no");

    // 2. 重定向到前端页面，并携带支付成功参数
    String redirectUrl = frontEndUrl + orderId;
    response.sendRedirect(redirectUrl);
  }
}
