package com.ayit.serialportdemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.ayit.serial_port_lib.AndroidSerialportApi;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private AndroidSerialportApi api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        api = new AndroidSerialportApi();

        api.open();

        api.setObserver(new AndroidSerialportApi.ReadDateObserver() {
            @Override
            public Map<Byte, byte[]> onProcessInSubThread(byte[] data) {

                Map<Byte,byte[]> map = new HashMap<>();

                for (int i=0;i<data.length;i++){
                    if (data[i] == 0x51){
                        map.put((byte) 0x51,data);
                        break;
                    }else if (data[i] == 0x50){
                        map.put((byte) 0x50,data);
                    }
                }

                return map;
            }

            @Override
            public void onObserveInMainThread(Map<Byte, byte[]> data) {
                for (Map.Entry<Byte, byte[]> entry:data.entrySet()){
                    Log.d(api.TAG,"接受："+ api.ByteArrToHex(entry.getValue()));
                }
            }

            @Override
            public void onTimeoutInMainThread(byte command, byte[] data) {
                Log.d(api.TAG,"超时："+ api.ByteArrToHex(data));

            }
        });
//        api.startKeepReadInSubThread();
    }

    public void send(View v){
        byte[] data = new byte[]{(byte) 0xaa ,0x55 ,0x02, 0x51 ,0x01};
        api.write(((byte)0x51),data,500);
    }


    public void start(View view){
        api.startKeepReadInSubThread();
    }

    public void stop(View view){
        api.stopKeepReadInSubThread();
    }
}
