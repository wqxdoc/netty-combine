package com.study.netty.codec.custom;

import com.study.netty.codec.CodecUtil;

import java.math.BigInteger;

public class ProtocolMsg {
    
    private ProtocolHeader protocolHeader = new ProtocolHeader();
    
    private String body;
    
    public ProtocolMsg() {
        
    }

    public ProtocolHeader getProtocolHeader() {
        return protocolHeader;
    }

    public void setProtocolHeader(ProtocolHeader protocolHeader) {
        this.protocolHeader = protocolHeader;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public static void main(String[] args) {
        String mockBytes = "5A54";
        mockBytes = mockBytes.replace(" ", "");
        byte[] bytecs = CodecUtil.hexStringToBytes(mockBytes);
        for (int i = 0; i < bytecs.length; i++) {
            System.out.println(bytecs[i]);
        }
        byte byteccs = (byte) Integer.parseInt(mockBytes, 16);
        System.out.println(byteccs);
        
        System.out.println(new String(bytecs));


        System.out.println(new BigInteger("5A", 16).toString());
    }

}
