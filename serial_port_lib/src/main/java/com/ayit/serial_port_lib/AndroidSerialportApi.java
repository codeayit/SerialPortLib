package com.ayit.serial_port_lib;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.Key;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android_serialport_api.SerialPort;


public class AndroidSerialportApi {

    private static final Handler handler = new Handler();
    private static final Map<Byte,SubThreadMsg> subThreadMsgMap = new HashMap<>();

    public static final String TAG = "android_serialport_api";

    //默认读取超时
    private final long def_read_time_out = 3 * 1000;
    //默认串口地址
    private final String def_serial_port = "/dev/ttyUSB0";
    //默认波特率
    private final int def_baudrate = 9600;
    //默认数据位
    private final int def_data_bits = 8;
    //默认数据校验  无校验
    private final char def_parity = 'N';
    //默认停止位
    private final int def_stop_bit = 1;


    private SerialPort mSerialPort;

    //串口地址
    private String serialPort;
    //波特率
    private int baudrate;
    //校验类型 取值
    // N  无
    // E  偶
    // O  奇
    private char parity;
    // 数据位
    private int dataBits;
    // 停止位
    private int stopBit;
    // 是否打开
    private boolean isOpened;

    private long readTimeOut;

    public void setReadTimeOut(long readTimeOut) {
        this.readTimeOut = readTimeOut;
    }

    private OutputStream mOutputStream;
    private InputStream mInputStream;

    public AndroidSerialportApi() {
        this.serialPort = def_serial_port;
        this.baudrate = def_baudrate;
        this.parity = def_parity;
        this.dataBits = def_data_bits;
        this.stopBit = def_stop_bit;
        this.readTimeOut = def_read_time_out;
    }

    public AndroidSerialportApi(String serialPort, int baudrate) {
        this.serialPort = serialPort;
        this.baudrate = baudrate;
        this.parity = def_parity;
        this.dataBits = def_data_bits;
        this.stopBit = def_stop_bit;
        this.readTimeOut = def_read_time_out;
    }

    public AndroidSerialportApi(String serialPort, int baudrate, char parity, int dataBits, int stopBit) {
        this.serialPort = serialPort;
        this.baudrate = baudrate;
        this.parity = parity;
        this.dataBits = dataBits;
        this.stopBit = stopBit;
    }

    public int open() {
        if (isOpened) {
            return 1;
        }

        File device = new File(serialPort);
        //检查访问权限，如果没有读写权限，进行文件操作，修改文件访问权限
        if (!device.canRead() || !device.canWrite()) {
            try {
                //通过挂在到linux的方式，修改文件的操作权限
                Process su = Runtime.getRuntime().exec("/system/bin/su");
                //一般的都是/system/bin/su路径，有的也是/system/xbin/su
                String cmd = "chmod 777 " + device.getAbsolutePath() + "\n" + "exit\n";
                su.getOutputStream().write(cmd.getBytes());

                if ((su.waitFor() != 0) || !device.canRead() || !device.canWrite()) {
                    Log.d("yc_log", "1111111111111");
                    throw new SecurityException();
                }
            } catch (Exception e) {
                e.printStackTrace();
                Log.d("yc_log", "22222222222222222222");
                throw new SecurityException();
            }
        }
        try {
            mSerialPort = new SerialPort(device, baudrate, dataBits, stopBit, parity);
            mOutputStream = mSerialPort.getOutputStream();
            mInputStream = mSerialPort.getInputStream();
            isOpened = true;
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }


    public byte[] read() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[128];
        long startTime = System.currentTimeMillis();

        int repCount = 5;

        while (true) {
            if (mInputStream.available() == 0) {
                repCount--;
                if (repCount <= 0 && System.currentTimeMillis() - startTime > readTimeOut) {
                    break;
                }
                SystemClock.sleep(10);
            } else {
                int size = mInputStream.read(buffer);
                out.write(buffer, 0, size);
                continue;
            }
            if (out.size() != 0 && mInputStream.available() == 0) {
                break;
            }
        }
        byte[] bytes = out.toByteArray();
        out.close();
        if (bytes.length == 0) {
            Log.d(TAG, "read_time_out:" + readTimeOut);
        } else {
            Log.d(TAG, "read：" + ByteArrToHex(bytes));
        }
        return bytes;

    }

    public byte[] read(int lenght) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        long startTime = System.currentTimeMillis();
        while (true) {
            if (mInputStream.available() == 0) {
                if (System.currentTimeMillis() - startTime > readTimeOut) {
                    break;
                }
                SystemClock.sleep(10);
            } else {
                int size = mInputStream.read(buffer);
                out.write(buffer, 0, size);
                continue;
            }
            if (out.size() == lenght) {
                break;
            }

        }
        byte[] bytes = out.toByteArray();
        out.close();
        Log.d(TAG, "read：" + ByteArrToHex(bytes));
        return bytes;

    }

    public void write(byte[] data) throws IOException {
        String writeStr = ByteArrToHex(data);
        Log.d(TAG, "write：" + writeStr);
        mOutputStream.write(data);
        SystemClock.sleep(150);
    }

    public void write(final byte command, final byte[] data, final long duration) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try{
                    String writeStr = ByteArrToHex(data);
                    Log.d(TAG, "write：" + writeStr);
                    subThreadMsg(command,1,data,duration);
                    mOutputStream.write(data);
                }catch (Exception e){
                    e.printStackTrace();
                }

            }
        }).start();

    }




    private synchronized Byte  subThreadMsg(byte command,int type,byte[] data,long duration){
        if (type ==1){
            //发送 添加消息
            SubThreadMsg msg = new SubThreadMsg(command,data,System.currentTimeMillis(),duration);
            Log.d(TAG,"添加消息："+ByteArrToHex(data));
            subThreadMsgMap.put(command,msg);
        }else  if (type == 2){
            // 接受 移除消息
            SubThreadMsg remove = subThreadMsgMap.remove(command);
            if (remove!=null){
                Log.d(TAG,"移除消息："+ByteArrToHex(remove.getData()));
                return remove.getCommand();
            }

        }else if ( type ==3){
            //检测超时
//            Log.d(TAG,"检测超时："+subThreadMsgMap.size()+ "   keys:" +subThreadMsgMap.keySet());
            Iterator<Map.Entry<Byte, SubThreadMsg>> it = subThreadMsgMap.entrySet().iterator();
            while(it.hasNext()){
                final Map.Entry<Byte, SubThreadMsg> entry = it.next();
                if(System.currentTimeMillis() - entry.getValue().getStartTime() > entry.getValue().getTimeoutDuration()){
                    it.remove();//使用迭代器的remove()方法删除元素
                    if (observer!=null){
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                observer.onTimeoutInMainThread(entry.getKey(),entry.getValue().getData());
                            }
                        });
                    }

                }
            }
        }
        return null;
    }

    private boolean keepRead = false;

    public void startKeepReadInSubThread() {
        keepRead = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "开始 1000 ms  延时");
                SystemClock.sleep(1000);
                keepRead = true;
                checkSubThreadTimeout();
                while (keepRead) {
                    try {
                        if (mInputStream.available() != 0) {
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            int count = 3;
                            while (true) {
                                if (mInputStream.available() != 0) {
                                    byte[] buffer = new byte[1024];
                                    int read = mInputStream.read(buffer);
                                    bos.write(buffer,0, read);
//                                    Log.d(TAG,"实时接受：:" +ByteArrToHex(bos.toByteArray()));
                                } else {
                                    if (--count == 0) {
                                        if (bos.size() > 0) {
                                            byte[] bytes = bos.toByteArray();
                                            bos.close();
                                            bos = null;
                                            if (observer != null) {
                                                final Map<Byte,byte[]> data = observer.onProcessInSubThread(bytes);
                                                for (Map.Entry<Byte,byte[]> entry:data.entrySet()){
                                                    subThreadMsg(entry.getKey(),2,null,0);
                                                }
//                                                Iterator<Map.Entry<Byte, byte[]>> it = data.entrySet().iterator();
//                                                while (it.hasNext()){
//                                                    Map.Entry<Byte, byte[]> entry = it.next();
//                                                    Byte b = subThreadMsg(entry.getKey(), 2, null, 0);
//                                                    if (b!=null){
//                                                        it.remove();
//                                                    }
//                                                }
//                                                observer.onObserve(data);
                                                if (data.size() > 0)
                                                    handler.post(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (observer != null) {
                                                                observer.onObserveInMainThread(data);
                                                            }
                                                        }
                                                    });
                                            }
                                        }
                                        break;
                                    }else{
                                    }
                                }
                                SystemClock.sleep(10);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    SystemClock.sleep(10);
                }

            }
        }).start();
    }

    public void stopKeepReadInSubThread() {
        keepRead = false;
    }

    private void checkSubThreadTimeout(){

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (keepRead){
                    subThreadMsg((byte) 1,3,null,0);
                    SystemClock.sleep(100);
                }
            }
        }).start();

    }


    public void close() {
        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
        }
        isOpened = false;
    }


    public boolean isOpened() {
        return isOpened;
    }


    //-------------------------------------------------------
    // 判断奇数或偶数，位运算，最后一位是1则为奇数，为0是偶数
    static public int isOdd(int num) {
        return num & 0x1;
    }

    //-------------------------------------------------------
    static public int HexToInt(String inHex)//Hex字符串转int
    {
        return Integer.parseInt(inHex, 16);
    }

    //-------------------------------------------------------
    static public byte HexToByte(String inHex)//Hex字符串转byte
    {
        return (byte) Integer.parseInt(inHex, 16);
    }

    //-------------------------------------------------------
    static public String Byte2Hex(Byte inByte)//1字节转2个Hex字符
    {
        return String.format("%02x", inByte).toUpperCase();
    }

    //-------------------------------------------------------
    public static  String ByteArrToHex(byte[] inBytArr)//字节数组转转hex字符串
    {
        StringBuilder strBuilder = new StringBuilder();
        int j = inBytArr.length;
        for (int i = 0; i < j; i++) {
            strBuilder.append(Byte2Hex(inBytArr[i]));
            strBuilder.append(" ");
        }
        return strBuilder.toString();
    }

    //-------------------------------------------------------
    static public String ByteArrToHex(byte[] inBytArr, int offset, int byteCount)//字节数组转转hex字符串，可选长度
    {
        StringBuilder strBuilder = new StringBuilder();
        int j = byteCount;
        for (int i = offset; i < j; i++) {
            strBuilder.append(Byte2Hex(inBytArr[i]));
        }
        return strBuilder.toString();
    }

    //-------------------------------------------------------
    //转hex字符串转字节数组
    static public byte[] HexToByteArr(String inHex)//hex字符串转字节数组
    {
        int hexlen = inHex.length();
        byte[] result;
        if (isOdd(hexlen) == 1) {//奇数
            hexlen++;
            result = new byte[(hexlen / 2)];
            inHex = "0" + inHex;
        } else {//偶数
            result = new byte[(hexlen / 2)];
        }
        int j = 0;
        for (int i = 0; i < hexlen; i += 2) {
            result[j] = HexToByte(inHex.substring(i, i + 2));
            j++;
        }
        return result;
    }

    private ReadDateObserver observer;

    public ReadDateObserver getObserver() {
        return observer;
    }

    public void setObserver(ReadDateObserver observer) {
        this.observer = observer;
    }

    public interface ReadDateObserver {
        /**
         *
         * @param data
         * k 功能码
         * v 指令数据
         * @return
         */
        Map<Byte,byte[]> onProcessInSubThread(byte[] data);

        /**
         *
         * @param data
         * k 功能码
         * v 指令数据
         * @return
         */
        void onObserveInMainThread(Map<Byte,byte[]> data);



        void onTimeoutInMainThread(byte command,byte[] data);
    }

    private static class SubThreadMsg{
        private byte command;
        private byte[] data;
        private long startTime;
        private long timeoutDuration;

        public SubThreadMsg(byte command, byte[] data, long startTime, long timeoutDuration) {
            this.command = command;
            this.data = data;
            this.startTime = startTime;
            this.timeoutDuration = timeoutDuration;
        }

        public byte getCommand() {
            return command;
        }

        public void setCommand(byte command) {
            this.command = command;
        }

        public byte[] getData() {
            return data;
        }

        public void setData(byte[] data) {
            this.data = data;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public long getTimeoutDuration() {
            return timeoutDuration;
        }

        public void setTimeoutDuration(long timeoutDuration) {
            this.timeoutDuration = timeoutDuration;
        }
    }

}
