package com.jonnyliu.proj.wechat.core;

import com.jonnyliu.proj.wechat.config.WechatConfig;
import com.jonnyliu.proj.wechat.converter.MessageConvert;
import com.jonnyliu.proj.wechat.exception.NoMessageHandlerFoundException;
import com.jonnyliu.proj.wechat.handler.AbstractMessageHandler;
import com.jonnyliu.proj.wechat.message.request.BaseRequestMessage;
import com.jonnyliu.proj.wechat.message.response.BaseResponseMessage;
import com.jonnyliu.proj.wechat.utils.MessageUtils;
import com.jonnyliu.proj.wechat.utils.SignUtil;
import com.sun.corba.se.impl.presentation.rmi.ExceptionHandlerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;

/**
 * Created by xbt on 2016/9/21 0021.
 */
@WebServlet(name = "WechatServlet",urlPatterns="/ws")
public class WechatServlet extends BaseServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(WechatServlet.class);

    @Autowired
    private MessageDispatcher messageDispatcher;

    @Autowired
    private MessageConvert messageConverter;

    /**
     * 接收微信服务器的post请求并响应
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        request.setCharacterEncoding("UTF-8");
        response.setCharacterEncoding("UTF-8");
        ServletInputStream inputStream = null;
        PrintWriter out = null;

        try {
            inputStream = request.getInputStream();
            out = response.getWriter();
            Map<String, String> map = MessageUtils.parseRequest(inputStream);
            String msgType = map.get("MsgType");
            String eventType = map.get("Event");
            //将用户发过来的消息转换成消息对象
            BaseRequestMessage requestMessage = messageConverter.doConvert(map);
            //将不同类型的消息发送给不同的消息处理器
            AbstractMessageHandler messageHandler = messageDispatcher.doDispatch(msgType,eventType);
            //调用消息处理器处理消息
            if (messageHandler == null) {
                throw new NoMessageHandlerFoundException("no message handler found for message type " + msgType + " and event type " + eventType);
            }
            BaseResponseMessage responseMessage = messageHandler.handleMessage(requestMessage);

            //构造给用户的响应消息
            String responseXml = MessageUtils.messageToXml(responseMessage);
            if (LOGGER.isDebugEnabled()){
                LOGGER.debug("response xml : {}",responseXml);
            }
            out.print(responseXml);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }finally {
            closeOutput(out);
        }
    }

    /**
     * 接收微信服务器的get请求
     *
     * @param request
     * @param response
     * @throws ServletException
     * @throws IOException
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response){
        String signature = request.getParameter("signature"); //签名
        String timestamp = request.getParameter("timestamp"); //时间戳
        String nonce = request.getParameter("nonce"); //随机数
        String echostr = request.getParameter("echostr"); //随机字符串
        PrintWriter out = null;

        try{
            out = response.getWriter();

            if (SignUtil.checkSignature(WechatConfig.getToken(), signature, timestamp, nonce)) {
                out.print(echostr);
            }
        }catch(Exception e){
            LOGGER.error(e.getMessage(), e);
        }finally{
            closeOutput(out);
        }
    }

    /**
     * 关闭out流
     * @param out
     */
    protected void closeOutput(PrintWriter out){
        try{
            if(out != null){
                out.flush();
                out.close();
            }
        }catch(Exception e){
            LOGGER.error(e.getMessage(), e);
        }
    }
}
