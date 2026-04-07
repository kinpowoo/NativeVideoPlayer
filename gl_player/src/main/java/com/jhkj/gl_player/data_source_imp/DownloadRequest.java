package com.jhkj.gl_player.data_source_imp;

/**
 * @param priority 优先级，数字越小优先级越高
 */ // 优先级下载请求
record DownloadRequest(long start, long end, int priority) implements Comparable<DownloadRequest> {

    @Override
    public int compareTo(DownloadRequest other) {
        return Integer.compare(this.priority, other.priority);
    }
}