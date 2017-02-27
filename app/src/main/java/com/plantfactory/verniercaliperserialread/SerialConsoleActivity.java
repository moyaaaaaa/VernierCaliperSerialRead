/* Copyright 2011-2013 Google Inc.
 * Copyright 2013 mike wakerly <opensource@hoho.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301,
 * USA.
 *
 * Project home page: https://github.com/mik3y/usb-serial-for-android
 */
package com.plantfactory.verniercaliperserialread;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.HexDump;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.plantfactory.verniercaliperserialread.MySoap;
import com.plantfactory.verniercaliperserialread.NfcReader;

/**
 * Monitors a single {@link UsbSerialPort} instance, showing all data
 * received.
 *
 * @author mike wakerly (opensource@hoho.com)
 */
public class SerialConsoleActivity extends Activity {
    private final String TAG = SerialConsoleActivity.class.getSimpleName();
    private String stringBuffer = "";
    //private double[] avrgBuffer = new double[1000];
    private ArrayList<Double> avrgBuffer = new ArrayList<Double>();
    private int avrgCnt = 0;

    /**
     * Driver instance, passed in statically via
     * {@link #show(Context, UsbSerialPort)}.
     *
     * <p/>
     * This is a devious hack; it'd be cleaner to re-create the driver using
     * arguments passed in with the {@link #startActivity(Intent)} intent. We
     * can get away with it because both activities will run in the same
     * process, and this is a simple demo.
     */
    private static UsbSerialPort sPort = null;

    private TextView mTitleTextView;
    private TextView averageTextView;
    private ListView listView;
    private CheckBox chkDTR;
    private CheckBox chkRTS;
    private CheckBox chkUpload;
    private Button AvrgButton;
    private Spinner spinner;

    private Uri uri;
    private Ringtone ringtone;
    private NfcAdapter nfcAdapter;
    private NfcReader nfcReader;
    private MyListArrayAdapter listArrayAdapter;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private SerialInputOutputManager mSerialIoManager;


    private final SerialInputOutputManager.Listener mListener =
            new SerialInputOutputManager.Listener() {

        @Override
        public void onRunError(Exception e) {
            Log.d(TAG, "Runner stopped.");
        }

        @Override
        public void onNewData(final byte[] data) {
            SerialConsoleActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //SerialConsoleActivity.this.updateReceivedData(data);
                    /*
                        CR来るまで読み，来たらparseして送信
                        format:"01A+00000.00"
                     */
                    if(!HexDump.toHexString(data).equals("0D")){
                        if(!new String(data).equals("+")) {
                            stringBuffer += new String(data);
                        }
                    }else{
                        stringBuffer = stringBuffer.replaceFirst("01A", "");
                        double dtmp = Double.parseDouble(stringBuffer);
                        String parsedValue = String.format("%.2f", dtmp);

                        //show
                        addListView(parsedValue + "mm");

                        //アップロード処理
                        if(chkUpload.isChecked()) { //逐次アップロード
                            new MySoap().execute(parsedValue, spinner.getSelectedItem().toString());
                        }
                        avrgBuffer.add(dtmp);
                        stringBuffer = "";

                        if(ringtone != null){
                            if(ringtone.isPlaying()) ringtone.stop();
                            ringtone.play(); //サウンド再生
                        }
                    }
                }
            });
        }
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.serial_console);
        mTitleTextView = (TextView) findViewById(R.id.demoTitle);
        averageTextView = (TextView) findViewById(R.id.averageTextView);
        listView = (ListView) findViewById(R.id.listView);
        chkDTR = (CheckBox) findViewById(R.id.checkBoxDTR);
        chkRTS = (CheckBox) findViewById(R.id.checkBoxRTS);
        chkUpload = (CheckBox) findViewById(R.id.checkBoxUpload);
        AvrgButton = (Button) findViewById(R.id.avrgButton);
        spinner = (Spinner)findViewById(R.id.spinner);
        uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        ringtone = RingtoneManager.getRingtone(getApplicationContext(), uri);
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        nfcReader = new NfcReader();

        //CheckBox処理
        chkDTR.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    sPort.setDTR(isChecked);
                }catch (IOException x){}
            }
        });
        chkRTS.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    sPort.setRTS(isChecked);
                }catch (IOException x){}
            }
        });


        //平均値算出処理
        AvrgButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //calc average
                double sum = 0;
                for(double v: avrgBuffer){
                    sum += v;
                }
                double average = sum / avrgBuffer.size();
                avrgBuffer.clear();
                listArrayAdapter.clear();
                listView.setAdapter(listArrayAdapter);

                //validation
                if(Double.isNaN(average)) {
                    Toast.makeText(getApplicationContext(), "データのアップロードに失敗しました", Toast.LENGTH_LONG).show();
                }else {
                    //upload
                    String parsedValue = String.format("%.2f", average);
                    String area = spinner.getSelectedItem().toString();
                    new MySoap().execute(parsedValue, area);

                    //show
                    averageTextView.setText("Average:" + parsedValue + "mm");
                    Toast.makeText(getApplicationContext(), "データをアップロードしました", Toast.LENGTH_LONG).show();
                }
            }
        });

        //リストの要素を削除する処理
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                avrgBuffer.remove(position);

                //listViewを更新
                ArrayList<String> stringBuffer = new ArrayList<String>();
                for(Double item :avrgBuffer) {
                    stringBuffer.add(String.format("%.2f", item));
                }
                CustomListData customListData = new CustomListData();
                listArrayAdapter.clear();
                for(String string :stringBuffer) {
                    customListData.setTextData(string + "mm");
                    listArrayAdapter.add(customListData);
                }
                listView.setAdapter(listArrayAdapter);
                Toast.makeText(getApplicationContext(), "データを削除しました", Toast.LENGTH_SHORT).show();
            }
        });

        //ListView設定
        CustomListData customListData = new CustomListData();
        List<CustomListData> customListView = new ArrayList<CustomListData>();
        listArrayAdapter = new MyListArrayAdapter(this, 0, customListView, R.layout.serial_console, R.layout.list_data);
        listView.setAdapter(listArrayAdapter);
    }


    //NFCがタッチされたときの処理
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Tag tag = (Tag) intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        if (tag == null) {
            // NFCタグがかざされたのとは異なる理由でインテントが飛んできた
            Log.d(TAG, "想定外のIntent受信です: action = " + intent.getAction());
            return;
        }

        Ndef ndefTag = Ndef.get(tag);
        if (ndefTag == null) {
            // NDEFフォーマットされていないタグがかざされた
            Log.d(TAG, "NDEF形式ではないタグがかざされました。");
            return;
        }
        String record = nfcReader.getRecord(intent);

        //NFCの文字列からspinnerを切り替える
        for(int i = 0; i < spinner.getCount(); i++){
            String itemText = spinner.getItemAtPosition(i).toString();
            if(itemText.equals(record)){
                spinner.setSelection(i);
            }
        }

        //message
        String area = spinner.getSelectedItem().toString();
        Toast.makeText(getApplicationContext(), "エリア" + area + "に変更しました" , Toast.LENGTH_LONG).show();
    }


    @Override
    protected void onPause() {
        super.onPause();
        stopIoManager();
        if (sPort != null) {
            try {
                sPort.close();
            } catch (IOException e) {
                // Ignore.
            }
            //sPort = null;
        }
        nfcAdapter.disableForegroundDispatch(this);
        //finish();
    }


    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "Resumed, port=" + sPort);
        if (sPort == null) {
            mTitleTextView.setText("No serial device.");
        } else {
            final UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

            UsbDeviceConnection connection = usbManager.openDevice(sPort.getDriver().getDevice());
            if (connection == null) {
                mTitleTextView.setText("Opening device failed");
                return;
            }

            try {
                sPort.open(connection);
                sPort.setParameters(2400, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

                /*
                showStatus(mDumpTextView, "CD  - Carrier Detect", sPort.getCD());
                showStatus(mDumpTextView, "CTS - Clear To Send", sPort.getCTS());
                showStatus(mDumpTextView, "DSR - Data Set Ready", sPort.getDSR());
                showStatus(mDumpTextView, "DTR - Data Terminal Ready", sPort.getDTR());
                showStatus(mDumpTextView, "DSR - Data Set Ready", sPort.getDSR());
                showStatus(mDumpTextView, "RI  - Ring Indicator", sPort.getRI());
                showStatus(mDumpTextView, "RTS - Request To Send", sPort.getRTS());
                */
            } catch (IOException e) {
                Log.e(TAG, "Error setting up device: " + e.getMessage(), e);
                mTitleTextView.setText("Error opening device: " + e.getMessage());
                try {
                    sPort.close();
                } catch (IOException e2) {
                    // Ignore.
                }
                sPort = null;
                return;
            }
            mTitleTextView.setText("Serial device: " + sPort.getClass().getSimpleName());
        }
        //NFC処理等
        Intent intent = new Intent(this, SerialConsoleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent =  PendingIntent.getActivity(this, 0, intent, 0);
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, null, null);

        chkDTR.setChecked(true);
        onDeviceStateChange();
    }

    private void stopIoManager() {
        if (mSerialIoManager != null) {
            Log.i(TAG, "Stopping io manager ..");
            mSerialIoManager.stop();
            mSerialIoManager = null;
        }
    }

    private void startIoManager() {
        if (sPort != null) {
            Log.i(TAG, "Starting io manager ..");
            mSerialIoManager = new SerialInputOutputManager(sPort, mListener);
            mExecutor.submit(mSerialIoManager);
        }
    }

    private void onDeviceStateChange() {
        stopIoManager();
        startIoManager();
    }

    void showStatus(TextView theTextView, String theLabel, boolean theValue){
        String msg = theLabel + ": " + (theValue ? "enabled" : "disabled") + "\n";
        theTextView.append(msg);
    }


    /**
     * Starts the activity, using the supplied driver instance.
     *
     * @param context
     * @param port
     */
    static void show(Context context, UsbSerialPort port) {
        sPort = port;
        final Intent intent = new Intent(context, SerialConsoleActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY);
        context.startActivity(intent);
    }

    /**
     * ListViewに表示する項目を追加する
     * @param string
     */
    private void addListView(String string) {
        CustomListData customListData = new CustomListData();
        customListData.setTextData(string);
        listArrayAdapter.add(customListData);
        listView.setAdapter(listArrayAdapter);
    }

}
