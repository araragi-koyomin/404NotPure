package com.example.tomatomall.controller;

import com.alibaba.fastjson.JSONObject;
import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.example.tomatomall.dto.PaymentData;
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

@RestController
@RequestMapping("/api/orders")
public class AliPayController {
  @Value("${alipay.app-id}")
  private String appId;
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
  private static final String FORMAT = "JSON";

  @Autowired
  private OrdersRepository ordersRepository;

  @Autowired
  private PaymentService paymentService;

  //vvvyyv9548@sandbox.com    支付邮箱
  @GetMapping("/pay") //subject=xxx&traceNo=xxx&totalAmount=xxx
  public void pay(AliPay aliPay, HttpServletResponse httpResponse) throws Exception {
    // 1. 创建Client，通用SDK提供的Client，负责调用支付宝的API
    AlipayClient alipayClient = new DefaultAlipayClient(serverUrl, appId,
        privateKey, FORMAT, charset, alipayPublicKey, signType);
    // 2. 创建 Request并设置Request参数
    AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();  // 发送请求的 Request类
    request.setNotifyUrl(notifyUrl);
    request.setReturnUrl(returnUrl);
    JSONObject bizContent = new JSONObject();
    bizContent.put("out_trade_no", aliPay.getTraceNo());  // 我们自己生成的订单编号
    bizContent.put("total_amount", aliPay.getTotalAmount()); // 订单的总金额
    bizContent.put("subject", aliPay.getSubject());   // 支付的名称
    bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");  // 固定配置
    request.setBizContent(bizContent.toString());
    // 执行请求，拿到响应的结果，返回给浏览器
    String form = "";
    try {
      form = alipayClient.pageExecute(request).getBody(); // 调用SDK生成表单
    } catch (AlipayApiException e) {
      e.printStackTrace();
    }
    httpResponse.setContentType("text/html;charset=" + charset);
    httpResponse.getWriter().write(form);// 直接将完整的表单html输出到页面
    httpResponse.getWriter().flush();
    httpResponse.getWriter().close();
  }

  @PostMapping("/{orderId}/pay")
  public Response<PaymentData> createPaymentForm(
      @PathVariable Integer orderId,
      HttpServletResponse response
  ) throws IOException, AlipayApiException {
    // 1. 验证订单存在且未支付
    Orders order = ordersRepository.findById(orderId)
        .orElseThrow(() -> new RuntimeException("订单不存在"));
    if (!"PENDING".equals(order.getStatus())) {
      throw new RuntimeException("订单状态异常");
    }

    // 2. 构建支付宝请求
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

    // 5. 构建响应对象
    PaymentData paymentData = new PaymentData();
    paymentData.setPaymentForm(form);
    paymentData.setOrderId(orderId.toString());
    paymentData.setTotalAmount(order.getTotalAmount().toString());
    paymentData.setPaymentMethod("Alipay");

    return Response.buildSuccess(paymentData);
  }

  @PostMapping("/notify")
  public void handleAlipayNotify(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // 1. 解析参数
    Map<String, String> params = request.getParameterMap().entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue()[0]));

    // 2. 验证签名
    try {
      boolean signVerified = AlipaySignature.rsaCheckV1(params, alipayPublicKey, "UTF-8", "RSA2");
      if (!signVerified) {
        response.getWriter().print("fail");
        return;
      }
    } catch (AlipayApiException e) {
      response.getWriter().print("fail");
      return;
    }

    // 3. 处理交易成功逻辑
    if ("TRADE_SUCCESS".equals(params.get("trade_status"))) {
      String orderId = params.get("out_trade_no");
      String alipayTradeNo = params.get("trade_no");
      String totalAmount = params.get("total_amount");

      try {
        paymentService.updateOrderStatus(orderId, alipayTradeNo, totalAmount);
      } catch (Exception e) {
        response.getWriter().print("fail");
        return;
      }
    }

    // 4. 返回success
    response.getWriter().print("success");
  }
  @GetMapping("/returnUrl")
  //此接口无需认证
  public void returnUrl(HttpServletRequest request, HttpServletResponse response) throws IOException {
    // 获取支付宝传递的参数
    String orderId = request.getParameter("out_trade_no");
    String tradeNo = request.getParameter("trade_no");

    // 构建前端URL，包含所有需要的参数
    // 注意：修改为您的实际前端地址，比如 http://localhost:3000/
    String frontendBaseUrl = "http://localhost:3000/";

    // 创建重定向URL，添加支付成功参数
    String redirectUrl = frontendBaseUrl + "?payment_success=true&orderId=" + orderId + "&tradeNo=" + tradeNo;

    // 重定向到前端应用
    response.sendRedirect(redirectUrl);
  }
}