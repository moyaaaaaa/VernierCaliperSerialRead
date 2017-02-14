package com.plantfactory.verniercaliperserialread;

import android.os.AsyncTask;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * soapプロトコルで送信する
 *
 * Created by Tomoya on 16/10/12.
 */
public class MySoap extends AsyncTask<String, Integer, Boolean> {
    private HttpURLConnection httpURLConnection;
    private URL url;
    private static final String host = "202.15.110.21";
    private static final String file = "/axis2/services/FIAPStorage";
    private static final String pointId = "http://j.kisarazu.ac.jp/PlantFactory/VernierCaliperTest/Value";
    private static final String body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\"><soapenv:Body><ns2:dataRQ xmlns:ns2=\"http://soap.fiap.org/\"><transport xmlns=\"http://gutp.jp/fiap/2009/11/\"><body><point id=\"%s\"><value time=\"%s\">%s</value></point></body></transport></ns2:dataRQ></soapenv:Body></soapenv:Envelope>";
    /*
    <?xml version="1.0" encoding="UTF-8"?>
    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
        <soapenv:Body>
            <ns2:dataRQ xmlns:ns2="http://soap.fiap.org/">
                <transport xmlns="http://gutp.jp/fiap/2009/11/">
                    <body>
                        <point id="%s">
                            <value time="%s">%s</value>
                        </point>
                    </body>
                </transport>
            </ns2:dataRQ>
        </soapenv:Body>
    </soapenv:Envelope>
    */

    public MySoap() {

    }

    @Override
    protected Boolean doInBackground(String... data) {
        return sendData(data[0], data[1]);
    }


    /**
     * areaからpointIDを自動生成して送信するラッパー
     * @param data
     * @param area
     * @return
     */
    public boolean sendData(String data, String area) {
        return sendMessage(data, pointId + "/" + area);
    }


    /**
     * 引数のpointId宛にdataを送信する
     * @param data
     * @param _pointId
     * @return
     */
    public boolean sendMessage(String data, String _pointId) {
        try {
            url = new URL("http", host, file);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("POST");
            httpURLConnection.setInstanceFollowRedirects(false);
            httpURLConnection.setRequestProperty("Content-Type", "text/xml;charset=UTF-8");
            httpURLConnection.setRequestProperty("SOAPAction", "http://soap.fiap.org/data\\");
            httpURLConnection.setDoOutput(true);
            httpURLConnection.setRequestProperty("Accept-Language", "jp");
            httpURLConnection.connect();
            PrintWriter printWriter = new PrintWriter(
                    new BufferedWriter(
                            new OutputStreamWriter(
                                    httpURLConnection.getOutputStream(),
                                    "UTF-8")
                    ));

            //送信するxml生成
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'+09:00'");
            String stringDate = simpleDateFormat.format(new Date()).toString();
            String content = String.format(body, _pointId, stringDate, data);

            printWriter.print(content);
            printWriter.close();

            //bodyの文字コード取得
            String contentType = httpURLConnection.getHeaderField("Content-Type");
            String charSet = "Shift-JIS";
            for(String v : contentType.replace(" ", "").split(";")){
                if(v.startsWith("charset=")){
                    charSet = v.substring(8);
                    break;
                }
            }
            //body受信
            BufferedReader bufferedReader;
            try{
                bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream(), charSet));
            }catch (Exception e){
                System.out.println(httpURLConnection.getResponseCode() + " " + httpURLConnection.getResponseMessage());
                bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getErrorStream(), charSet));
            }
            String line;
            while((line = bufferedReader.readLine()) != null) {
                System.out.println(line + "¥n");
            }
            bufferedReader.close();
            httpURLConnection.disconnect();
        }catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }
}
