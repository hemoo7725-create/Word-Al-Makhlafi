package com.almakhlafi.saw;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.*;
public class MainActivity extends Activity {
 public void onCreate(Bundle b){super.onCreate(b); TextView t=new TextView(this); t.setText("المخلافي صوت\n\nجاهز"); t.setTextSize(24); t.setTextColor(Color.rgb(19,121,91)); t.setGravity(17); setContentView(t);}
}
