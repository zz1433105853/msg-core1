
package com.ty.modules.tunnel.send.container.impl;

import com.ty.common.utils.IdGen;
import com.ty.modules.msg.entity.Tunnel;
import com.ty.modules.tunnel.reply.disruptor.MessageReplyEventProducer;
import com.ty.modules.tunnel.report.disruptor.MessageReportEventProducer;
import com.ty.modules.tunnel.response.disruptor.MessageResponseEventProducer;
import com.ty.modules.tunnel.send.container.entity.CmppMessageSend;
import com.ty.modules.tunnel.send.container.entity.MessageSend;
import com.ty.modules.tunnel.send.container.entity.cmpp.*;
import com.ty.modules.tunnel.send.container.mina.CmppMessagerHandler;
import com.ty.modules.tunnel.send.container.mina.MsgUtils;
import org.apache.log4j.Logger;
import org.apache.mina.core.session.IoSession;
import org.apache.mina.filter.codec.ProtocolCodecFilter;
import org.apache.mina.filter.logging.LoggingFilter;

import java.util.Date;

/**
 * Created by Ysw on 2016/5/24.
 * CMPP 移动短信接口
 */
public class CmppMessagerContainer extends AbstractStraightMessageContainer{

    private static Logger logger= Logger.getLogger(CmppMessagerContainer.class);

    public CmppMessagerContainer(
            Tunnel tunnel,
            MsgConfig msgConfig,
            LoggingFilter loggingFilter,
            ProtocolCodecFilter codecFilter,
            MessageResponseEventProducer messageResponseEventProducer,
            MessageReportEventProducer messageReportEventProducer,
            MessageReplyEventProducer messageReplyEventProducer) {
        super(tunnel, msgConfig, loggingFilter, codecFilter, messageResponseEventProducer, messageReportEventProducer, messageReplyEventProducer);
        messageHandler = new CmppMessagerHandler(this, messageResponseEventProducer, messageReportEventProducer, messageReplyEventProducer);
        init();
    }


    /**
     * 创建Socket链接后请求链接ISMG
     * @return
     */
    public void connectISMG(IoSession ioSession){
        MsgConnect connect=new MsgConnect();
        connect.setTotalLength(12+6+16+1+4);//消息总长度，级总字节数:4+4+4(消息头)+6+16+1+4(消息主体)
        connect.setCommandId(MsgCommand.CMPP_CONNECT);//标识创建连接
        connect.setSequenceId(getSequence());//序列，由我们指定
        connect.setSourceAddr(msgConfig.getSpId());//我们的企业代码
        String timestamp = MsgUtils.getTimestamp();
        connect.setAuthenticatorSource(MsgUtils.getAuthenticatorSource(msgConfig.getSpId(),msgConfig.getSecret(), timestamp));//md5(企业代码+密匙+时间戳)
        connect.setTimestamp(Integer.parseInt(timestamp));//时间戳(MMDDHHMMSS)
        if(ioSession==null) {

            logger.error(new StringBuilder().append(this.getTdName()).append("ioSession是空的啊！！！！！！！！！！！"));
        }
        ioSession.write(connect);
    }

    /**
     * 发送短短信
     * @param msg 短信内容
     * @param destTermId 接收短信号码
     * @return
     */
    public boolean sendShortMsg(String msg,String destTermId, String srcId, MessageSend ms){
        CmppMessageSend messageSend = (CmppMessageSend) ms;
        try {
            while(!checkWindowMapSize()) {
                try {
                    logger.info(new StringBuilder().append(this.getTdName()).append("等待窗口中..........."));
                    outTimeShutDown();
                    //printDataInWindowMap();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    return false;
                }
            }

            int seqId=getSequence();
            MsgSubmit submit=new MsgSubmit();
            /**
             * 12: Head
             * 8: Msg_Id
             * 1: Pk_total
             * 1: Pk_number
             * 1: Registered_Delivery
             * 1: Msg_level
             * 10: Service_Id
             * 1: Fee_UserType
             * 32: Fee_terminal_Id (2.0为 21 )
             * 1: Fee_terminal_type (3.0新增)
             * 1: TP_pId
             * 1: TP_udhi
             * 1: Msg_Fmt
             * 6: Msg_src
             * 2: FeeType
             * 6: FeeCode
             * 17: ValId_Time
             * 17: At_Time
             * 21: Src_Id
             * 1: DestUsr_tl
             * 32: Dest_terminal_Id （2.0为21）
             * 1: Dest_terminal_type (3.0新增)
             * 1: Msg_Length
             * 20: LinkID (3.0新增)
             */
            boolean is3_0 = true;
            long totalLength = 0l;
            if(!"3".equals(msgConfig.getProtocolVersion())) {
                //2.0
                is3_0 = false;
                totalLength = 12+8+1+1+1+1+10+1+21+1+1+1+6+2+6+17+17+21+1+21+1+msg.getBytes("GBK").length+8;
            }else {
                //3.0
                totalLength = 12+8+1+1+1+1+10+1+32+1+1+1+1+6+2+6+17+17+21+1+32+1+1+msg.getBytes("GBK").length+20;
            }
            submit.setTotalLength(totalLength);
            submit.setCommandId(MsgCommand.CMPP_SUBMIT);
            submit.setSequenceId(seqId);
            submit.setPkTotal((byte)0x01);
            submit.setPkNumber((byte)0x01);
            submit.setRegisteredDelivery((byte)0x01);
            submit.setMsgLevel((byte)0x01);
            submit.setFeeUserType((byte)0x02);
            submit.setFeeTerminalId("");
            submit.setServiceId(msgConfig.getServiceId());
            submit.setFeeTerminalType((byte)0x00); //(3.0新增)
            submit.setTpPId((byte)0x00);
            submit.setTpUdhi((byte)0x00);
            submit.setMsgFmt((byte)0x0f);
            submit.setMsgSrc(msgConfig.getSpId());
            submit.setSrcId(srcId);
            submit.setDestTerminalId(destTermId);
            submit.setMsgLength((byte) msg.getBytes("GBK").length);
            submit.setMsgContent(msg.getBytes("GBK"));
            submit.setId(IdGen.uuid());

            logger.info(
                    new StringBuilder()
                            .append("******************************************************\n")
                            .append(this.getTdName()).append("向[").append(destTermId).append("]下发短短信:{").append(msg).append("}，序列号为:").append(seqId).append("\n")
                            .append("******************************************************\n"));
            messageSend.setMsgSubmitNow(submit);
            messageSend.setIntoWindowMapTime(new Date());
            windowMap.put(String.valueOf(seqId), messageSend);
            this.limitCounter.doIncrement();
            ioSession.write(submit);
            return true;
        } catch (Exception e) {

            logger.error(new StringBuilder().append(this.getTdName()).append("发送短短信失败:").append(e.getMessage()));
            return false;
        }
    }


    /**
     * 发送长短信
     * @param msg 短信内容
     * @param destTermId 接收短信号码
     * @return
     */
    public boolean sendLongMsg(String msg,String destTermId, String srcId, MessageSend ms){
        CmppMessageSend messageSend = (CmppMessageSend) ms;
        try {
            byte[] allByte=msg.getBytes("UTF-16BE");
            int msgByteLength=allByte.length;
            int maxByteLength=134;
            int msgSendCount=msgByteLength%maxByteLength==0?msgByteLength/maxByteLength : msgByteLength/maxByteLength+1;

            String[] longToshort = generateLongMsgShortArr(msg);


            //短信息内容头拼接
            byte[] msgHead=new byte[6];
            msgHead[0]=0x05;
            msgHead[1]=0x00;
            msgHead[2]=0x03;
            msgHead[3]=(byte)getSequence();
            msgHead[4]=(byte)msgSendCount;
            msgHead[5]=0x01;
            int seqId=0;
            for(int i=0;i<longToshort.length;i++) {
                msgHead[5]=(byte)(i+1);
                byte[] needMsg=longToshort[i].getBytes("UTF-16BE");
                int subLength=needMsg.length+msgHead.length;
                byte[] sendMsg=new byte[needMsg.length+msgHead.length];
                System.arraycopy(msgHead,0,sendMsg,0,6);
                System.arraycopy(needMsg,0,sendMsg,6,needMsg.length);
                MsgSubmit submit=new MsgSubmit();

                boolean is3_0 = true;
                long totalLength = 0l;
                if(!"3".equals(msgConfig.getProtocolVersion())) {
                    //2.0
                    is3_0 = false;
                    totalLength = 12+8+1+1+1+1+10+1+21+1+1+1+6+2+6+17+17+21+1+21+1+subLength+8;
                }else {
                    //3.0
                    totalLength = 12+8+1+1+1+1+10+1+32+1+1+1+1+6+2+6+17+17+21+1+32+1+1+subLength+20;
                }
                submit.setTotalLength(totalLength);
                submit.setCommandId(MsgCommand.CMPP_SUBMIT);
                seqId = getSequence();
                submit.setSequenceId(seqId);
                submit.setPkTotal((byte)msgSendCount);
                submit.setPkNumber((byte)(i+1));
                submit.setRegisteredDelivery((byte)0x01);
                submit.setMsgLevel((byte)0x01);
                submit.setFeeUserType((byte)0x02);
                submit.setFeeTerminalId("");
                submit.setFeeTerminalType((byte)0x00);
                submit.setTpPId((byte)0x00);
                submit.setTpUdhi((byte)0x01);
                submit.setMsgFmt((byte)0x08);
                submit.setMsgSrc(msgConfig.getSpId());
                submit.setServiceId(msgConfig.getServiceId());
                submit.setSrcId(srcId);
                submit.setDestTerminalId(destTermId);
                submit.setMsgLength((byte) sendMsg.length);
                submit.setMsgContent(sendMsg);

                boolean sendIntervalRight = true;
                while(!(sendIntervalRight = checkSendInterval())) {
                    try {
                        if(!sendIntervalRight) {

                            logger.info(new StringBuilder().append(this.getTdName()).append("长短信等待最佳发送时间......"));
                        }
                        sendIntervalRight = true;
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        return false;
                    }
                }
                while(!checkWindowMapSize()) {
                    try {

                        logger.info(new StringBuilder().append(this.getTdName()).append("长短信等待窗口中..........."));
                        outTimeShutDown();
                        //printDataInWindowMap();
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return false;
                    }
                }

                CmppMessageSend messageSendSub = new CmppMessageSend(messageSend.getMsgRecord());
                messageSendSub.setSrcId(messageSend.getSrcId());
                messageSendSub.setTunnel(messageSend.getTunnel());
                messageSendSub.setMsgSubmitNow(submit);//本次提交短信实体
                messageSendSub.setIntoWindowMapTime(new Date());
                windowMap.put(String.valueOf(seqId), messageSendSub);

                this.limitCounter.doIncrement();
                ioSession.write(submit);
            }
            return true;
        } catch (Exception e){
            e.printStackTrace();

            logger.error(new StringBuilder().append(this.getTdName()).append("发送长短信失败:").append(e.getMessage()));
            return false;
        }
    }

    /**
     * CMPP_DELIVER_RESP消息定义（SP -> ISMG）
     * @param msg_Id
     * @param result :
     *               0：正确；
     *               1：消息结构错；
    2：命令字错；
    3：消息序号重复；
    4：消息长度错；
    5：资费代码错；
    6：超过最大信息长；
    7：业务代码错；
    8: 流量控制错；
    9~ ：其他错误。
     */
    public void sendMsgDeliverResp(long msg_Id,long seqId,int result){
        try {
            MsgDeliverResp msgDeliverResp=new MsgDeliverResp();
            msgDeliverResp.setTotalLength(12+8+4);
            msgDeliverResp.setCommandId(MsgCommand.CMPP_DELIVER_RESP);
            msgDeliverResp.setSequenceId(seqId);
            msgDeliverResp.setMsg_Id(msg_Id);
            msgDeliverResp.setResult(result);
            ioSession.write(msgDeliverResp);
        }catch (Exception e) {
            e.printStackTrace();

            logger.error(new StringBuilder().append("发送DeliverResp失败 消息ID : ").append(msg_Id).append(" | Reuslt: ").append(result));
        }
    }

    public void sendActiveTestResp(long seqId){
        try {
            MsgActiveTestResp msgActiveTestResp=new MsgActiveTestResp();
            msgActiveTestResp.setTotalLength(13);
            msgActiveTestResp.setCommandId(MsgCommand.CMPP_ACTIVE_TEST_RESP);
            msgActiveTestResp.setSequenceId(seqId);
            msgActiveTestResp.setReserved((byte)0);
            ioSession.write(msgActiveTestResp);
        }catch (Exception e) {
            e.printStackTrace();
            logger.error("发送链路检测失败："+seqId+e.getMessage());
        }
    }

    /**
     * 发送web push短信
     * @param messageSend
     * @return
     */
    public boolean sendWapPushMsg(MessageSend messageSend){
        return false;
    }


    /**
     * 发送web push 短短信
     * @param url wap网址
     * @param desc 描述
     * @param destTermId 短信
     * @return
     */
    public boolean sendShortWapPushMsg(String url,String desc,String destTermId){
       return false;
    }
    /**
     * 发送web push 长短信
     * @param url wap网址
     * @param desc 描述
     * @param destTermId 短信
     * @return
     */
    public boolean sendLongWapPushMsg(String url,String desc,String destTermId){
        return false;
    }







}


