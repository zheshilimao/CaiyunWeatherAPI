package com.api.caiyunweather;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class SettingsActivity extends AppCompatActivity {
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable bg(String color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.parseColor(color));
        d.setCornerRadius(dp(radius));
        return d;
    }
    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.rgb(190,211,230)); t.setTextSize(13);
        t.setPadding(dp(2),0,dp(2),dp(7));
        return t;
    }
    private EditText edit(String value) {
        EditText e = new EditText(this);
        e.setText(value); e.setTextColor(Color.WHITE); e.setTextSize(15); e.setSingleLine(true);
        e.setPadding(dp(13),0,dp(13),0); e.setBackground(bg("#235985", 13));
        return e;
    }
    private LinearLayout card() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(17),dp(16),dp(17),dp(16));
        l.setBackground(bg("#174F827D",20));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.setMargins(0,dp(10),0,0); l.setLayoutParams(p);
        return l;
    }
    private TextView note(String s) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextColor(Color.rgb(143,174,199)); t.setTextSize(12);
        t.setPadding(0,dp(8),0,0);
        return t;
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(7,27,61));
        getWindow().setNavigationBarColor(Color.rgb(7,27,61));

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(7,27,61));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8),dp(8),dp(12),dp(8));
        TextView back = new TextView(this);
        back.setText("‹"); back.setTextColor(Color.WHITE); back.setTextSize(34); back.setGravity(Gravity.CENTER);
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));
        back.setOnClickListener(v->finish());
        TextView title = new TextView(this);
        title.setText("设置"); title.setTextColor(Color.WHITE); title.setTextSize(20); title.setTypeface(null,1);
        title.setPadding(dp(4),0,0,0);
        bar.addView(back); bar.addView(title,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(bar);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16),0,dp(16),dp(24));
        scroll.addView(content);

        LinearLayout apiCard = card();
        apiCard.addView(label("API Key"));
        EditText key = edit(AppConfig.getApiKey(this));
        // API Key 直接显示，不再使用容易出现状态切换问题的“显示/隐藏”按钮。
        key.setInputType(InputType.TYPE_CLASS_TEXT);
        apiCard.addView(key,new LinearLayout.LayoutParams(-1,dp(48)));
        apiCard.addView(note("请手动填写你的彩云天气 API Key。App 不内置任何 Key。"));
        content.addView(apiCard);

        LinearLayout endpointCard=card();
        endpointCard.addView(label("API 地址"));
        EditText base=edit(AppConfig.getApiBase(this));
        endpointCard.addView(base,new LinearLayout.LayoutParams(-1,dp(48)));
        endpointCard.addView(note("接口根地址，例如 https://api.caiyunapp.com/v2.6，一般不用动，修改了好像也没问题。"));
        content.addView(endpointCard);

        LinearLayout locCard=card();
        LinearLayout locRow=new LinearLayout(this);
        locRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout txt=new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        TextView lt=new TextView(this);
        lt.setText("自动获取当前位置"); lt.setTextColor(Color.WHITE); lt.setTextSize(16);
        TextView ls=new TextView(this);
        ls.setText("开启后使用手机定位；关闭后使用下面手动填写的位置");
        ls.setTextColor(Color.rgb(143,174,199)); ls.setTextSize(12); ls.setPadding(0,dp(4),0,0);
        txt.addView(lt); txt.addView(ls);
        locRow.addView(txt,new LinearLayout.LayoutParams(0,-2,1));
        SwitchCompat sw=new SwitchCompat(this);
        sw.setChecked(AppConfig.isAutoLocation(this));
        locRow.addView(sw); locCard.addView(locRow);

        TextView manualTitle = label("手动位置");
        manualTitle.setPadding(dp(2),dp(16),dp(2),dp(7));
        locCard.addView(manualTitle);

        EditText lat = edit(AppConfig.hasManualLocation(this) ? String.valueOf(AppConfig.getLat(this)) : "");
        lat.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        locCard.addView(label("纬度"));
        locCard.addView(lat,new LinearLayout.LayoutParams(-1,dp(48)));

        EditText lon = edit(AppConfig.hasManualLocation(this) ? String.valueOf(AppConfig.getLon(this)) : "");
        lon.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        locCard.addView(label("经度"));
        locCard.addView(lon,new LinearLayout.LayoutParams(-1,dp(48)));

        EditText place = edit(AppConfig.getPlace(this).equals(AppConfig.DEFAULT_PLACE) ? "" : AppConfig.getPlace(this));
        locCard.addView(label("位置名称（可选）"));
        locCard.addView(place,new LinearLayout.LayoutParams(-1,dp(48)));
        locCard.addView(note("例如：南宁市 良庆区。自动定位关闭后，将使用这里的经纬度。天气数据依赖经纬度返回，位置名称图一乐。"));
        content.addView(locCard);

        LinearLayout refreshCard=card();
        refreshCard.addView(label("自动刷新间隔（分钟）"));
        EditText refresh=edit(String.valueOf(AppConfig.getRefreshMin(this)));
        refresh.setInputType(InputType.TYPE_CLASS_NUMBER);
        refreshCard.addView(refresh,new LinearLayout.LayoutParams(-1,dp(48)));
        refreshCard.addView(note("范围 1～120 分钟；下拉刷新仍可随时手动刷新。"));
        content.addView(refreshCard);

        TextView current=new TextView(this);
        current.setText("当前保存位置："+AppConfig.getPlace(this)+"\n"+AppConfig.getLon(this)+", "+AppConfig.getLat(this));
        current.setTextColor(Color.rgb(143,174,199)); current.setTextSize(12);
        current.setPadding(dp(4),dp(14),dp(4),0); content.addView(current);

        TextView save=new TextView(this);
        save.setText("保存设置"); save.setTextColor(Color.WHITE); save.setTextSize(15); save.setGravity(Gravity.CENTER);
        save.setBackground(bg("#2C7FB1",15));
        LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,dp(50));
        sp.setMargins(0,dp(18),0,0); content.addView(save,sp);

        save.setOnClickListener(v->{
            String k=key.getText().toString().trim();
            String b=base.getText().toString().trim();
            if(k.isEmpty()){Toast.makeText(this,"API Key 不能为空",Toast.LENGTH_SHORT).show();return;}
            if(b.isEmpty()){Toast.makeText(this,"API 地址不能为空",Toast.LENGTH_SHORT).show();return;}

            int refreshMin;
            try { refreshMin=Integer.parseInt(refresh.getText().toString().trim()); }
            catch(Exception e){ Toast.makeText(this,"刷新时间请输入数字",Toast.LENGTH_SHORT).show(); return; }
            if(refreshMin<1 || refreshMin>120){Toast.makeText(this,"刷新时间范围为 1～120 分钟",Toast.LENGTH_SHORT).show();return;}

            double la=AppConfig.getLat(this), lo=AppConfig.getLon(this);
            if(!lat.getText().toString().trim().isEmpty() || !lon.getText().toString().trim().isEmpty()){
                try {
                    la=Double.parseDouble(lat.getText().toString().trim());
                    lo=Double.parseDouble(lon.getText().toString().trim());
                } catch(Exception e){ Toast.makeText(this,"经纬度格式不正确",Toast.LENGTH_SHORT).show(); return; }
                if(la < -90 || la > 90 || lo < -180 || lo > 180){
                    Toast.makeText(this,"纬度范围 -90～90，经度范围 -180～180",Toast.LENGTH_SHORT).show(); return;
                }
            } else if(!sw.isChecked()) {
                Toast.makeText(this,"关闭自动定位时，请填写经纬度",Toast.LENGTH_SHORT).show(); return;
            }

            String placeName=place.getText().toString().trim();
            AppConfig.save(this,k,b,sw.isChecked(),refreshMin);
            if(!lat.getText().toString().trim().isEmpty() && !lon.getText().toString().trim().isEmpty()) {
                AppConfig.saveLocation(this,la,lo,placeName);
            }
            Toast.makeText(this,"设置已保存",Toast.LENGTH_SHORT).show();
            finish();
        });

        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
}
