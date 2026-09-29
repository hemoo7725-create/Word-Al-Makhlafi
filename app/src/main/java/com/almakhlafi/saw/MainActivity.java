package com.almakhlafi.saw;
import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.speech.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
 EditText editor; TextView status; Button mic; SpeechRecognizer sr; boolean listening=false; String pendingHtml="";
 Map<String,String> dict=new LinkedHashMap<>(); android.content.SharedPreferences prefs;
 int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 TextView t(String s,float z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setGravity(17);v.setPadding(dp(6),dp(4),dp(6),dp(4));return v;}
 Button b(String s){Button x=new Button(this);x.setText(s);return x;}
 public void onCreate(Bundle q){super.onCreate(q);prefs=getSharedPreferences("dict",0);load();ui();setup();if(android.os.Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},44);}
 void ui(){
  LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.VERTICAL);r.setPadding(dp(10),dp(8),dp(10),dp(8));r.setBackgroundColor(Color.rgb(245,250,247));r.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
  LinearLayout h=new LinearLayout(this);h.setOrientation(LinearLayout.VERTICAL);h.setGravity(17);h.setBackgroundColor(Color.rgb(19,121,91));h.addView(t("المخلافي صوت",25,-1),new LinearLayout.LayoutParams(-1,dp(42)));h.addView(t("تحويل الكلام إلى نص عربي قابل للتحرير",13,-1),new LinearLayout.LayoutParams(-1,dp(28)));r.addView(h,new LinearLayout.LayoutParams(-1,dp(76)));
  status=t("جاهز. اضغط ابدأ التسجيل.",13,Color.rgb(23,53,42));r.addView(status,new LinearLayout.LayoutParams(-1,dp(42)));
  LinearLayout rr=new LinearLayout(this);mic=b("🎙 ابدأ التسجيل");Button stop=b("■ إيقاف");rr.addView(mic,new LinearLayout.LayoutParams(0,dp(52),1));rr.addView(stop,new LinearLayout.LayoutParams(0,dp(52),1));r.addView(rr);mic.setOnClickListener(v->start());stop.setOnClickListener(v->stop());
  editor=new EditText(this);editor.setTextSize(18);editor.setGravity(Gravity.TOP|Gravity.RIGHT);editor.setHint("سيظهر النص هنا...");editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);editor.setPadding(dp(12),dp(12),dp(12),dp(12));editor.setBackgroundColor(Color.WHITE);r.addView(editor,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout a=new LinearLayout(this);Button copy=b("نسخ"),clear=b("مسح"),word=b("Word"),d=b("قاموسي");for(Button x:new Button[]{copy,clear,word,d})a.addView(x,new LinearLayout.LayoutParams(0,dp(52),1));r.addView(a);
  copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("المخلافي صوت",editor.getText()));status.setText("تم النسخ.");});clear.setOnClickListener(v->editor.setText(""));word.setOnClickListener(v->word());d.setOnClickListener(v->dialog());setContentView(r);
 }
 void setup(){if(!SpeechRecognizer.isRecognitionAvailable(this)){status.setText("التعرف الصوتي غير متاح على الجهاز.");return;}sr=SpeechRecognizer.createSpeechRecognizer(this);sr.setRecognitionListener(new RecognitionListener(){
  public void onReadyForSpeech(Bundle x){status.setText("أستمع الآن...");} public void onBeginningOfSpeech(){status.setText("تحدث الآن...");} public void onRmsChanged(float x){} public void onBufferReceived(byte[] x){} public void onEndOfSpeech(){status.setText("جارٍ التحليل...");}
  public void onError(int x){listening=false;mic.setText("🎙 ابدأ التسجيل");status.setText("انتهى التسجيل. حاول مرة أخرى.");}
  public void onResults(Bundle x){ArrayList<String>a=x.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);if(a!=null&&!a.isEmpty())add(a.get(0));if(listening)start();} public void onPartialResults(Bundle x){} public void onEvent(int x,Bundle y){}
 });}
 void start(){if(sr==null){setup();if(sr==null)return;}Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-YE");i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ar-YE");i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);listening=true;mic.setText("🎙 التسجيل مستمر");sr.startListening(i);}
 void stop(){listening=false;if(sr!=null)sr.stopListening();mic.setText("🎙 ابدأ التسجيل");status.setText("متوقف.");}
 void add(String s){String o=norm(cmd(s));String old=editor.getText().toString();if(!old.isEmpty()&&!old.endsWith("\n")&&!o.startsWith(")")&&!o.startsWith("،")&&!o.startsWith(".")&&!o.startsWith(":"))old+=" ";editor.setText(old+o);editor.setSelection(editor.length());status.setText("تمت إضافة النص.");}
 String cmd(String s){s=s.replaceAll("افتح\\s+قوس","(").replaceAll("(?:اغلقه|أغلقه|اقفله|أقفل(?:ه)?)",")").replaceAll("نقطتين\\s+فوق\\s+بعض",":").replaceAll("فاصلة","،").replaceAll("نقطة","\\.");Matcher m=Pattern.compile("فقرة\\s+(?:رقم\\s*)?([0-9٠-٩]+)").matcher(s);StringBuffer z=new StringBuffer();while(m.find())m.appendReplacement(z,Matcher.quoteReplacement(".\n"+western(m.group(1))+"/"));m.appendTail(z);return z.toString().replaceAll("فقرة\\s+(?:الف|ألف|الألف)",".\nأ/");}
 String norm(String s){String[][]p={{"الاستناف","الاستئناف"},{"المحكمه","المحكمة"},{"القضيه","القضية"},{"الدعوي","الدعوى"},{"مسوليه","مسؤولية"},{"مسؤوليه","مسؤولية"},{"هيئه","هيئة"},{"المحاماه","المحاماة"},{"اثبات","إثبات"},{"مسئولية","مسؤولية"}};for(String[]x:p)s=s.replace(x[0],x[1]);for(Map.Entry<String,String>x:dict.entrySet())s=s.replace(x.getKey(),x.getValue());return s.replaceAll("\\s+([،.:)])","$1").replaceAll("\\(\\s+","(").trim();}
 String western(String s){return s.replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');}
 void dialog(){LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(14),0,dp(14),0);EditText a=new EditText(this);a.setHint("النطق/الكلمة");EditText b=new EditText(this);b.setHint("النص الصحيح");x.addView(a);x.addView(b);AlertDialog d=new AlertDialog.Builder(this).setTitle("قاموس المخلافي").setMessage("أضف المصطلحات اليمنية والقضائية الخاصة بك.").setView(x).setNegativeButton("إغلاق",null).setPositiveButton("حفظ",null).create();d.setOnShowListener(v->d.getButton(-1).setOnClickListener(q->{if(a.length()>0&&b.length()>0){dict.put(a.getText().toString().trim(),b.getText().toString().trim());save();status.setText("تم حفظ المصطلح.");d.dismiss();}}));d.show();}
 void load(){String s=prefs.getString("items","");if(!s.isEmpty())for(String q:s.split("\\|")){int k=q.indexOf("=>");if(k>0)dict.put(q.substring(0,k),q.substring(k+2));}}
 void save(){StringBuilder s=new StringBuilder();for(Map.Entry<String,String>x:dict.entrySet()){if(s.length()>0)s.append("|");s.append(x.getKey().replace("|"," ")).append("=>").append(x.getValue().replace("|"," "));}prefs.edit().putString("items",s.toString()).apply();}
 void word(){String x=editor.getText().toString().trim();if(x.isEmpty()){status.setText("لا يوجد نص.");return;}pendingHtml="<html><head><meta charset='UTF-8'></head><body dir='rtl' style='font-family:Arial;line-height:1.8;font-size:16pt'>"+esc(x).replace("\n","<br>")+"</body></html>";Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/msword");i.putExtra(Intent.EXTRA_TITLE,"المخلافي-صوت.doc");startActivityForResult(i,88);}
 protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==88&&c==RESULT_OK&&d!=null)try{java.io.OutputStream o=getContentResolver().openOutputStream(d.getData());o.write(pendingHtml.getBytes("UTF-8"));o.close();status.setText("تم تصدير Word قابل للتحرير.");}catch(Exception e){status.setText("تعذر حفظ الملف.");}}
 String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}
 protected void onDestroy(){if(sr!=null)sr.destroy();super.onDestroy();}
}
