package com.ty.modules.tunnel.send.container.impl;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.ty.common.mapper.JaxbMapper;
import com.ty.common.utils.StringUtils;
import com.ty.modules.tunnel.entity.MsgReply;
import com.ty.modules.msg.entity.MsgResponse;
import com.ty.modules.msg.entity.Tunnel;
import com.ty.modules.tunnel.reply.disruptor.MessageReplyEventProducer;
import com.ty.modules.tunnel.report.disruptor.MessageReportEventProducer;
import com.ty.modules.tunnel.response.disruptor.MessageResponseEventProducer;
import com.ty.modules.tunnel.send.container.entity.MessageSend;
import com.ty.modules.tunnel.send.container.entity.ThirdMessageSendAh;
import com.ty.modules.tunnel.send.container.entity.container.ah.ThirdAhBalanceResult;
import com.ty.modules.tunnel.send.container.entity.container.ah.ThirdAhMoReportResult;
import com.ty.modules.tunnel.send.container.entity.container.ah.ThirdAhSendMsgResult;
import com.ty.modules.tunnel.send.container.entity.container.ah.ThirdAhSendReportResult;
import com.ty.modules.sys.service.MsgRecordService;
import com.ty.modules.sys.service.MsgReplyService;
import com.ty.modules.sys.service.MsgReportService;
import com.ty.modules.sys.service.MsgResponseService;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * Created by ljb on 2017/4/22 10:06.
 */
public class ThirdAhMessagerContainer extends AbstractThirdPartyMessageContainer {

    private static Logger logger= Logger.getLogger(ThirdAhMessagerContainer.class);

    private String url;
    private String userId;
    private String account;
    private String password;

    public ThirdAhMessagerContainer(Tunnel tunnel, MessageResponseEventProducer messageResponseEventProducer, MessageReportEventProducer messageReportEventProducer, MessageReplyEventProducer messageReplyEventProducer, MsgRecordService msgRecordService, MsgResponseService msgResponseService, MsgReportService msgReportService, MsgReplyService msgReplyService) {
        super(tunnel, messageResponseEventProducer, messageReportEventProducer, messageReplyEventProducer, msgRecordService, msgResponseService, msgReportService, msgReplyService);
        if(tunnel!=null) {
            this.url = tunnel.getUrl();
            this.account = tunnel.getAccount();
            String[] accountUserId = account.split(",");
            if(accountUserId.length == 2){
                this.userId = accountUserId[0];
                this.account = accountUserId[1];
            }
            this.password = tunnel.getPassword();
        }
    }

    @Override
    public long getBalance() {
        HttpResponse<String> result = null;
        try {
            result = Unirest.post(this.url+"/sms.aspx")
                    .field("action", "overage")
                    .field("userid", this.userId)
                    .field("account",this.account)
                    .field("password",this.password)
                    .asString();
            String xmlResult = result.getBody();
            ThirdAhBalanceResult thirdTxctSendMsgResult = JaxbMapper.fromXml(xmlResult, ThirdAhBalanceResult.class);
            return thirdTxctSendMsgResult.getOverage();
        } catch (UnirestException e) {
            return 0;
        }
    }

    @Override
    public void triggerMsgReportFetch() {
        try {
            HttpResponse<String> result = Unirest.post(this.url+"/statusApi.aspx")
                    .field("action", "query")
                    .field("userid", this.userId)
                    .field("account", this.account)
                    .field("password", this.password).asString();
            String xmlResult = result.getBody();
            logger.info(this.getTdName()+": result "+xmlResult);
            ThirdAhSendReportResult thirdTxctSendReportResult = JaxbMapper.fromXml(xmlResult, ThirdAhSendReportResult.class);
            if(thirdTxctSendReportResult!=null && thirdTxctSendReportResult.getReturnsms() != null) {
                messageReportEventProducer.onData(thirdTxctSendReportResult.getMsgReportList());
            }else{
                logger.info("返回状态报告为空");
            }
        } catch (Exception e) {
            logger.info("获取状态报告异常"+e.getMessage());
        }
    }

    @Override
    public void triggerMsgReplyFetch() {
        try {
            HttpResponse<String> result = Unirest.post(this.url+"/callApi.aspx")
                    .field("action", "query")
                    .field("userid", this.account)
                    .field("account", this.account)
                    .field("password", this.password).asString();
            String xmlResult = result.getBody();
            logger.info(this.getTdName()+": result "+xmlResult);
            ThirdAhMoReportResult thirdTxctMoReportResult = JaxbMapper.fromXml(xmlResult, ThirdAhMoReportResult.class);
            if(thirdTxctMoReportResult!=null && thirdTxctMoReportResult.getReturnsms() != null) {

                Map<String, MsgResponse> mrMap = msgResponseService.loadMsgResponseInfoMapByMsgIds(thirdTxctMoReportResult.getAssocatedCustomerIds());

                List<MsgReply> msgReplyList = thirdTxctMoReportResult.getMsgReplyList(mrMap);

                messageReplyEventProducer.onData(msgReplyList);

            }else{
                logger.info("返回状态报告为空");
            }
        } catch (Exception e) {
            logger.info("获取状态报告异常"+e.getMessage());
        }
    }

    @Override
    public boolean sendMsg(MessageSend ms) {
        ThirdMessageSendAh messageSend = (ThirdMessageSendAh) ms;
        String mobile = messageSend.getMobile();
        String content = messageSend.getContent();
        try {
            HttpResponse<String> result = Unirest.post(this.url+"/sms.aspx")
                    .field("action", "send")
                    .field("userid", this.userId)
                    .field("account", this.account)
                    .field("password", this.password)
                    .field("mobile", mobile)
                    .field("content", content)
                    .field("extno", messageSend.getSrcId())
                    .field("sendTime", "").asString();
            String xmlResult = result.getBody();
            logger.info(StringUtils.builderString(this.getTdName(), xmlResult));
            ThirdAhSendMsgResult thirdAhSendMsgResult = JaxbMapper.fromXml(xmlResult, ThirdAhSendMsgResult.class);
            if(thirdAhSendMsgResult!=null) {
                messageSend.setThirdAhSendMsgResult(thirdAhSendMsgResult);
                messageSend.setRemarks(xmlResult);
                messageResponseEventProducer.onData(messageSend.getMsgResponse());
                return true;
            }else  {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }
}
