package com.jhkj.gl_player.data_source_imp;

// 缓冲块结构
public class BufferBlock {
    long startPos;
    byte[] data;
    int length;
    long timestamp;

    BufferBlock(long startPos, byte[] data, int length) {
        this.startPos = startPos;
        this.data = data;
        this.length = length;
        this.timestamp = System.currentTimeMillis();
    }

    boolean contains(long position, int size) {
        if(position < startPos)return false;
        return (position + size) <= (startPos + length);
    }
}