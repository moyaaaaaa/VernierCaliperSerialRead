package com.plantfactory.verniercaliperserialread;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Tomoya on 17/02/21.
 */
public class MyListArrayAdapter extends ArrayAdapter<CustomListData> {
    private Context context;
    private LayoutInflater inflater;
    private int listItemLayoutId, baseLayoutId;
    private List<CustomListData> customListView = new ArrayList<CustomListData>();
    private SerialConsoleActivity serialConsoleActivity;

    public MyListArrayAdapter(Context context, int resource, List<CustomListData> customListView, int baseLayoutId, int listItemLayoutid) {
        super(context, resource, customListView);

        this.context = context;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        this.listItemLayoutId = listItemLayoutid;
        this.baseLayoutId = baseLayoutId;
        this.customListView = customListView;
        serialConsoleActivity = new SerialConsoleActivity();
    }

    public View getView(final int position, View convertView, final ViewGroup parent)
    {
        if(convertView == null){
            convertView = inflater.inflate(listItemLayoutId, null);
        }
        final View _convertView = convertView;
        View baseView = inflater.inflate(baseLayoutId, null);

        CustomListData item = this.getItem(position);
        //リストのアイテムデータの取得

        TextView textView = (TextView)convertView.findViewById(R.id.listTextView);
        if(textView != null){
            textView.setText(item.getTextData());
            //アイテムデータに設定されたテキストを表示
        }

        Button deleteButton = (Button) convertView.findViewById(R.id.deleteButton);
        final ListView listView = (ListView) parent;
        final MyListArrayAdapter myListArrayAdapter = this;
        deleteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AdapterView.OnItemClickListener listener = listView.getOnItemClickListener();
                long id = getItemId(position);
                listener.onItemClick((AdapterView<?>)parent, view, position, id);
            }
        });

        return convertView;
    }
}
