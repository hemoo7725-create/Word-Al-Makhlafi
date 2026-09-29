package com.almakhlafi.saw;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.speech.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    EditText editor;
    TextView status, subjectView;
    Button mic;
    SpeechRecognizer sr;
    boolean listening=false, destroying=false;
    String pendingHtml="";
    String subject="";
    Map<String,String> dict=new LinkedHashMap<>();
    android.content.SharedPreferences prefs;

    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    TextView t(String s,float z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setGravity(17);v.setPadding(dp(6),dp(4),dp(6),dp(4));return v;}
    Button b(String s){Button x=new Button(this);x.setText(s);x.setTextSize(13);return x;}

    public void onCreate(Bundle q){
        super.onCreate(q);
        prefs=getSharedPreferences("almakhlafi_data",0);
        loadDictionary();
        buildUi();
        setupRecognizer();
        if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},44);
        new Handler().postDelayed(()->newSubject(false),350);
    }

    void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(8),dp(7),dp(8),dp(7));
        root.setBackgroundColor(Color.rgb(245,250,247));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout head=new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setGravity(17);
        head.setBackgroundColor(Color.rgb(19,121,91));
        head.addView(t("المخلافي صوت",24,Color.WHITE),new LinearLayout.LayoutParams(-1,dp(38)));
        subjectView=t("لا يوجد موضوع مفتوح",14,Color.WHITE);
        head.addView(subjectView,new LinearLayout.LayoutParams(-1,dp(30)));
        root.addView(head,new LinearLayout.LayoutParams(-1,dp(70)));

        LinearLayout top=new LinearLayout(this); top.setGravity(17);
        Button newTopic=b("＋ موضوع جديد"), history=b("📚 سجل المواضيع");
        top.addView(newTopic,new LinearLayout.LayoutParams(0,dp(48),1));
        top.addView(history,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(top);
        newTopic.setOnClickListener(v->newSubject(true));
        history.setOnClickListener(v->showHistory());

        status=t("جاهز. افتح موضوعًا ثم اضغط ابدأ التسجيل.",13,Color.rgb(23,53,42));
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(38)));

        LinearLayout rec=new LinearLayout(this); rec.setGravity(17);
        mic=b("🎙 ابدأ التسجيل"); Button stop=b("■ إيقاف");
        rec.addView(mic,new LinearLayout.LayoutParams(0,dp(54),1));
        rec.addView(stop,new LinearLayout.LayoutParams(0,dp(54),1));
        root.addView(rec);
        mic.setOnClickListener(v->startListening());
        stop.setOnClickListener(v->stopListening());

        editor=new EditText(this);
        editor.setTextSize(18);
        editor.setGravity(Gravity.TOP|Gravity.RIGHT);
        editor.setHint("سيظهر النص هنا...");
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setPadding(dp(12),dp(12),dp(12),dp(12));
        editor.setBackgroundColor(Color.WHITE);
        editor.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.addView(editor,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout a=new LinearLayout(this); a.setGravity(17);
        Button save=b("حفظ باسم"), word=b("Word"), copy=b("نسخ"), clear=b("مسح"), dictionary=b("قاموسي");
        for(Button x:new Button[]{save,word,copy,clear,dictionary})
            a.addView(x,new LinearLayout.LayoutParams(0,dp(50),1));
        root.addView(a);

        save.setOnClickListener(v->saveCurrent());
        word.setOnClickListener(v->exportWord());
        copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(subject,editor.getText()));status.setText("تم نسخ النص.");});
        clear.setOnClickListener(v->editor.setText(""));
        dictionary.setOnClickListener(v->dictionaryDialog());

        setContentView(root);
    }

    void newSubject(boolean ask){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),0,dp(12),0);
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint("مثال: موضوع القضية رقم 25");
        box.addView(name);
        AlertDialog d=new AlertDialog.Builder(this)
            .setTitle("فتح موضوع جديد")
            .setMessage("اكتب اسم الموضوع قبل بدء العمل. سيُستخدم الاسم عند الحفظ والتصدير إلى Word.")
            .setView(box).setNegativeButton(ask?"إلغاء":"لاحقًا",null).setPositiveButton("فتح الموضوع",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{
            String n=name.getText().toString().trim();
            if(n.isEmpty()){name.setError("اكتب اسم الموضوع");return;}
            if(!subject.isEmpty() && editor.length()>0) saveCurrent();
            subject=n;
            subjectView.setText("الموضوع: "+subject);
            editor.setText("");
            addHistory(subject);
            status.setText("تم فتح الموضوع: "+subject);
            d.dismiss();
        }));
        d.setOnCancelListener(x->{if(subject.isEmpty()){subject="موضوع جديد";subjectView.setText("الموضوع: "+subject);status.setText("يمكنك تغيير الاسم من «موضوع جديد».");}});
        d.show();
    }

    void addHistory(String s){
        LinkedHashSet<String> set=new LinkedHashSet<>();
        String raw=prefs.getString("history","");
        if(!raw.isEmpty()) set.addAll(Arrays.asList(raw.split("\\|")));
        set.remove(s); set.add(s);
        prefs.edit().putString("history",join(set,"|")).apply();
    }
    String join(Collection<String> c,String sep){StringBuilder b=new StringBuilder();for(String s:c){if(b.length()>0)b.append(sep);b.append(s.replace("|"," "));}return b.toString();}

    void showHistory(){
        String raw=prefs.getString("history","");
        ArrayList<String> items=new ArrayList<>();
        if(!raw.isEmpty()) for(String s:raw.split("\\|")) if(!s.trim().isEmpty()) items.add(s);
        if(items.isEmpty()){new AlertDialog.Builder(this).setTitle("سجل المواضيع").setMessage("لا توجد مواضيع محفوظة بعد.").setPositiveButton("إغلاق",null).show();return;}
        Collections.reverse(items);
        String[] arr=items.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("سجل المواضيع")
            .setItems(arr,(d,which)->openHistoryTopic(arr[which]))
            .setNegativeButton("إغلاق",null).show();
    }

    void openHistoryTopic(String name){
        if(!subject.isEmpty() && editor.length()>0) saveCurrent();
        subject=name; subjectView.setText("الموضوع: "+subject);
        String saved=prefs.getString("draft_"+safeKey(subject),"");
        editor.setText(saved);
        editor.setSelection(editor.length());
        status.setText("تم فتح الموضوع: "+subject);
    }


    void saveCurrent(){
        if(subject==null || subject.trim().isEmpty()){newSubject(true);return;}
        prefs.edit().putString("draft_"+safeKey(subject),editor.getText().toString()).apply();
        addHistory(subject);
        status.setText("تم حفظ الموضوع باسم: "+subject);
    }

    void setupRecognizer(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){status.setText("التعرف الصوتي غير متاح على هذا الجهاز.");return;}
        sr=SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle x){status.setText("أستمع الآن... التسجيل مستمر حتى تضغط إيقاف.");}
            public void onBeginningOfSpeech(){status.setText("تحدث الآن...");}
            public void onRmsChanged(float x){}
            public void onBufferReceived(byte[] x){}
            public void onEndOfSpeech(){status.setText("أعيد الاستماع...");}
            public void onError(int e){
                if(listening && !destroying){ new Handler().postDelayed(()->{if(listening&&!destroying)listenOnce();},250); }
                else {mic.setText("🎙 ابدأ التسجيل");status.setText("تم إيقاف التسجيل.");}
            }
            public void onResults(Bundle x){
                ArrayList<String>a=x.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(a!=null&&!a.isEmpty()) appendProcessed(a.get(0));
                if(listening&&!destroying) new Handler().postDelayed(()->{if(listening&&!destroying)listenOnce();},180);
            }
            public void onPartialResults(Bundle x){}
            public void onEvent(int x,Bundle y){}
        });
    }

    void startListening(){
        if(subject.isEmpty() || subject.equals("موضوع جديد")) newSubject(true);
        if(sr==null){setupRecognizer();if(sr==null)return;}
        listening=true; mic.setText("🎙 التسجيل مستمر — اضغط إيقاف");
        status.setText("التسجيل مستمر...");
        listenOnce();
    }

    void listenOnce(){
        if(!listening||destroying||sr==null)return;
        try{
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-YE");
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ar-YE");
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
            sr.startListening(i);
        }catch(Exception e){if(listening)new Handler().postDelayed(()->listenOnce(),500);}
    }

    void stopListening(){
        listening=false;
        if(sr!=null)sr.stopListening();
        mic.setText("🎙 ابدأ التسجيل");
        status.setText("تم إيقاف التسجيل.");
        saveCurrent();
    }

    void appendProcessed(String s){
        String o=normalize(processCommands(s));
        if(o.trim().isEmpty())return;
        String old=editor.getText().toString();
        if(!old.isEmpty()&&!old.endsWith("\n")&&!o.startsWith(")")&&!o.startsWith("،")&&!o.startsWith(".")&&!o.startsWith(":")&&!o.startsWith("؟")&&!o.startsWith("!")&&!o.startsWith("؛")&&!o.startsWith("”")) old+=" ";
        editor.setText(old+o); editor.setSelection(editor.length());
        status.setText("تمت إضافة النص — التسجيل مستمر.");
    }

    String processCommands(String s){
        s=s.replaceAll("(?i)(افتح|فتح|ضع|حط|اعمل|سوي)\\s+(قوس|القوس)","(");
        s=s.replaceAll("(?i)(اغلق|أغلق|سكر|اقفل|أقفل)(\\s+|\\s*)(قوس|القوس|ه)?",")");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(فاصلة|فاصله)", "،");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(نقطة|نقطه)", ".");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(علامة\\s*استفهام|علامة\\s*السؤال|استفهام|سؤال)", "؟");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(علامة\\s*تعجب|علامة\\s*التعجب|تعجب)", "!");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(نقطتين|نقطتين\\s+فوق\\s+بعض|نقطتان)", ":");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(فاصلة\\s*منقوطة|فاصله\\s*منقوطه|منقوطة)", "؛");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(شرطة|خط)", "-");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(سطر\\s*جديد|سطر جديد|انزل سطر|فقرة جديدة)", "\n");
        s=s.replaceAll("(?i)(افتح|فتح)\\s+(اقتباس|علامة اقتباس)", "“");
        s=s.replaceAll("(?i)(اغلق|أغلق|سكر|اقفل|أقفل)\\s+(اقتباس|علامة اقتباس)", "”");

        Matcher m=Pattern.compile("فقرة\\s+(?:رقم\\s*)?([0-9٠-٩]+)").matcher(s);
        StringBuffer z=new StringBuffer();
        while(m.find())m.appendReplacement(z,Matcher.quoteReplacement(".\n"+western(m.group(1))+"/"));
        m.appendTail(z); s=z.toString();
        s=s.replaceAll("(?i)فقرة\\s+(?:الف|ألف|الألف)",".\nأ/");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(تنوين\\s*فتح|تنوين\\s*بالفتح)", "__TAN_FATHA__");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(تنوين\\s*ضم|تنوين\\s*بالضم)", "__TAN_DAMMA__");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(تنوين\\s*كسر|تنوين\\s*بالكسر)", "__TAN_KASRA__");
        return s;
    }

    String normalize(String s){
        String[][]p={
            {"الاستناف","الاستئناف"},{"الاستأناف","الاستئناف"},{"المحكمه","المحكمة"},{"القضيه","القضية"},
            {"الدعوي","الدعوى"},{"مسوليه","مسؤولية"},{"مسؤوليه","مسؤولية"},{"مسئولية","مسؤولية"},{"هيئه","هيئة"},
            {"المحاماه","المحاماة"},{"اثبات","إثبات"},{"الاثبات","الإثبات"},{"المساله","المسألة"},{"مساله","مسألة"},
            {"الاجراءات","الإجراءات"},{"اجراءات","إجراءات"},{"الاحكام","الأحكام"},{"احكام","أحكام"},{"الادعاء","الادعاء"},
            {"الادله","الأدلة"},{"ادله","أدلة"},{"الاجتماع","الاجتماع"},{"المسؤوليه","المسؤولية"},{"مسوولية","مسؤولية"},
            {"المبرره","المبررة"},{"المقدمه","المقدمة"},{"المستأنفه","المستأنفة"},{"المستأنف عليه","المستأنف عليه"},
            {"المحاماة","المحاماة"},{"القاضيه","القاضية"},{"المدعيه","المدعية"},{"المدعاه","المدعاة"},
            {"تعويضات","تعويضات"},{"تعويضه","تعويضه"},{"قضيه","قضية"},{"دعوي","دعوى"},{"هيئه","هيئة"}
        };
        for(String[]x:p)s=s.replace(x[0],x[1]);
        for(Map.Entry<String,String>x:dict.entrySet())s=s.replace(x.getKey(),x.getValue());
        s=applyTanwin(s);
        return s.replaceAll("\\s+([،.:؟!؛)])","$1").replaceAll("\\(\\s+","(").replaceAll("\\s+([”])","$1").trim();
    }

    String applyTanwin(String s){
        s=s.replace("__TAN_FATHA__","").replace("__TAN_DAMMA__","").replace("__TAN_KASRA__","");
        // Common spoken/legal words with established forms; avoid blanket guessing.
        String[][] words={
            {"تماما","تمامًا"},{"فعلا","فعلًا"},{"غالبا","غالبًا"},{"عادة","عادةً"},{"خصوصا","خصوصًا"},
            {"مبدئيا","مبدئيًا"},{"نهائيا","نهائيًا"},{"قانونا","قانونًا"},{"شرعا","شرعًا"},{"حكما","حكمًا"},
            {"بناءا","بناءً"},{"وفقا","وفقًا"},{"استنادا","استنادًا"},{"تبعا","تبعًا"},{"صراحة","صراحةً"},
            {"ابتداءا","ابتداءً"},{"وانتهاءا","وانتهاءً"},{"مثلا","مثلًا"},{"إجمالا","إجمالًا"},{"تفصيلا","تفصيلًا"}
        };
        for(String[]x:words)s=s.replaceAll("(?<![\u0627-\u064A])"+Pattern.quote(x[0])+"(?![\u0627-\u064A])",x[1]);
        return s;
    }

    String western(String s){return s.replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');}

    void dictionaryDialog(){
        LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(14),0,dp(14),0);
        EditText a=new EditText(this);a.setHint("الكلمة/النطق الذي يظهر خطأ");EditText b=new EditText(this);b.setHint("الكتابة الصحيحة (مع الهمز والتاء والتنوين إن لزم)");
        x.addView(a);x.addView(b);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("قاموس المخلافي")
            .setMessage("يمكنك إضافة أي مصطلح يمني أو قضائي وتصحيحه تلقائيًا.")
            .setView(x).setNegativeButton("إغلاق",null).setPositiveButton("حفظ",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(q->{String aa=a.getText().toString().trim(),bb=b.getText().toString().trim();if(!aa.isEmpty()&&!bb.isEmpty()){dict.put(aa,bb);saveDictionary();status.setText("تم حفظ المصطلح.");d.dismiss();}}));
        d.show();
    }

    void loadDictionary(){String s=prefs.getString("dictionary","");if(!s.isEmpty())for(String q:s.split("\\|")){int k=q.indexOf("=>");if(k>0)dict.put(q.substring(0,k),q.substring(k+2));}}
    void saveDictionary(){prefs.edit().putString("dictionary",joinDict()).apply();}
    String joinDict(){StringBuilder s=new StringBuilder();for(Map.Entry<String,String>x:dict.entrySet()){if(s.length()>0)s.append("|");s.append(x.getKey().replace("|"," ")).append("=>").append(x.getValue().replace("|"," "));}return s.toString();}

    void exportWord(){
        String x=editor.getText().toString().trim();
        if(x.isEmpty()){status.setText("لا يوجد نص للتصدير.");return;}
        if(subject.isEmpty()) {newSubject(true);return;}
        pendingHtml="<html><head><meta charset='UTF-8'></head><body dir='rtl' style='font-family:Arial;line-height:1.8;font-size:16pt'><h2 style='text-align:center'>"+esc(subject)+"</h2>"+esc(x).replace("\n","<br>")+"</body></html>";
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.setType("application/msword");
        i.putExtra(Intent.EXTRA_TITLE,safeFileName(subject)+".doc");
        startActivityForResult(i,88);
    }

    String safeFileName(String s){String x=s.replaceAll("[\\\\/:*?\"<>|]","_").trim();return x.isEmpty()?"موضوع":x;}

    protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==88&&c==RESULT_OK&&d!=null)try{
            java.io.OutputStream o=getContentResolver().openOutputStream(d.getData());
            o.write(pendingHtml.getBytes("UTF-8"));o.close();
            saveCurrent();status.setText("تم تصدير Word باسم الموضوع: "+subject);
        }catch(Exception e){status.setText("تعذر حفظ ملف Word.");}
    }

    String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}

    @Override protected void onPause(){super.onPause();if(isFinishing())stopListening();}
    @Override protected void onDestroy(){destroying=true;listening=false;if(sr!=null)sr.destroy();super.onDestroy();}
}