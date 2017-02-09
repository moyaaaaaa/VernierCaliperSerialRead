package com.plantfactory.verniercaliperserialread;

/**
 * Created by Tomoya on 17/02/07.
 */
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Parcelable;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;

/**
 * NFCの情報を呼んだりするクラス
 */
public class NfcReader {
    public NfcReader(){

    }


    /**
     * IDmを取得する
     * @param intent 受信インテント
     * @return IDm文字列
     */
    public String getIdm(Intent intent) {
        String idm = null;
        StringBuffer idmByte = new StringBuffer();
        byte[] rawIdm = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);

        if (rawIdm != null) {
            for (int i = 0; i < rawIdm.length; i++) {
                idmByte.append(Integer.toHexString(rawIdm[i] & 0xff));
            }
            idm = idmByte.toString();
        }
        return idm;
    }


    /**
     * recordのメッセージを取得する
     * @param intent 受信インテント
     * @return String文字列
     */
    public String getRecord(Intent intent) {
        Parcelable[] res = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
        NdefMessage ndefMessage = (NdefMessage) res[0];
        NdefRecord[] ndefRecords = ndefMessage.getRecords();

        for(NdefRecord record : ndefRecords){
            System.out.println(record);

            if(record.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(record.getType(), NdefRecord.RTD_TEXT)){
                if (record == null)
                    throw new IllegalArgumentException();

                byte[] payload = record.getPayload();
                byte flags = payload[0];
                String encoding = ((flags & 0x80) == 0) ? "UTF-8" : "UTF-16";
                int languageCodeLength = flags & 0x3F;
                try {
                    String languageCode = new String(payload, 1, languageCodeLength, "US-ASCII");
                    String text = new String(payload, 1 + languageCodeLength, payload.length - (1 + languageCodeLength), encoding);
                    return String.format("%s", text);
                } catch (UnsupportedEncodingException e) {
                    throw new IllegalArgumentException();
                } catch (IndexOutOfBoundsException e) {
                    throw new IllegalArgumentException();
                }
            }
        }

        return null;
    }
}