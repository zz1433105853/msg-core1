package com.ty.modules.tunnel.send.container.entity.container.klws;

public class Arrivedklws {
    private String msgid;
    private String mobile;
    private String stat;
    private String arrivedTime;


    public Arrivedklws() {
    }

    public Arrivedklws(String msgid, String mobile, String stat, String arrivedTime) {
        this.msgid = msgid;
        this.mobile = mobile;
        this.stat = stat;
        this.arrivedTime=arrivedTime;
    }

    public String getMsgid() {
        return msgid;
    }

    public void setMsgid(String msgid) {
        this.msgid = msgid;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getStat() {

        return stat;
    }

    public void setStat(String stat) {
        this.stat = stat;
    }
    public String getArrivedTime() {
        return arrivedTime;
    }

    public void setArrivedTime(String arrivedTime) {
        this.arrivedTime = arrivedTime;
    }

}
