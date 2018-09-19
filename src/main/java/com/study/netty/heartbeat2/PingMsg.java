package com.study.netty.heartbeat2;

public class PingMsg extends BaseMsg{
    
    public PingMsg() {
        super();
        setType(MsgType.PING);
    }

}
