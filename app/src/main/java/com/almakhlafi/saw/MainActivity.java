package com.almakhlafi.saw;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.os.*;
import android.speech.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    EditText editor;
    TextView status, subjectView, liveView, countView;
    Button mic, stop;
    View rootView;
    boolean showRecognitionChanges=true, keepScreenOn=true, mutePhone=false, muteMedia=false, showErrors=true, longSpeech=true, drawLines=false, dynamicFont=false;
    int mainButtonHeight=58;
    float editorFontSize=18f;
    int savedRing=-1, savedNotification=-1, savedMusic=-1;
    ScaleGestureDetector scaleDetector;
    SpeechRecognizer sr;
    Handler handler = new Handler();
    boolean listening=false, destroying=false, starting=false, appInForeground=true;
    String pendingHtml="", subject="", committedText="", liveText="";
    Map<String,String> dict=new LinkedHashMap<>();
    android.content.SharedPreferences prefs;

    final int GREEN=Color.rgb(17,122,91), DARK=Color.rgb(15,55,43), LIGHT=Color.rgb(246,250,248);
    final int GREEN2=Color.rgb(226,244,237);

    int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    TextView tv(String s,float z,int c){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);return v;}
    Button btn(String s){Button x=new Button(this);x.setText(s);x.setTextSize(13);x.setAllCaps(false);x.setSoundEffectsEnabled(false);return x;}
    GradientDrawableBox cardBg(int color){return new GradientDrawableBox(color,dp(16));}

    public void onCreate(Bundle q){
        super.onCreate(q);
        prefs=getSharedPreferences("almakhlafi_data",0);
        loadDictionary();
        buildUi();
        loadSettings();
        setupRecognizer();
        if(Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},44);
        handler.postDelayed(()->newSubject(false),350);
    }

    void buildUi(){
        LinearLayout root=new LinearLayout(this);
        rootView=root;
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(10),dp(10),dp(10),dp(10));
        root.setBackgroundColor(LIGHT);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header=new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(18),dp(12),dp(18),dp(12));
        header.setBackground(cardBg(GREEN));
        TextView title=tv("المخلافي صوت",25,Color.WHITE);
        title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        header.addView(title,new LinearLayout.LayoutParams(-1,dp(38)));
        subjectView=tv("لا يوجد موضوع مفتوح",13,Color.rgb(225,255,242));
        header.addView(subjectView,new LinearLayout.LayoutParams(-1,dp(28)));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(82)));

        LinearLayout tools=new LinearLayout(this);
        tools.setPadding(0,dp(7),0,dp(5));
        Button newTopic=btn("＋ موضوع جديد"), history=btn("📚 سجل المواضيع"), settings=btn("⚙ الإعدادات");
        tools.addView(newTopic,new LinearLayout.LayoutParams(0,dp(46),1));
        tools.addView(history,new LinearLayout.LayoutParams(0,dp(46),1));
        tools.addView(settings,new LinearLayout.LayoutParams(0,dp(46),1));
        root.addView(tools);
        newTopic.setOnClickListener(v->newSubject(true));
        history.setOnClickListener(v->showHistory());
        settings.setOnClickListener(v->settingsDialog());

        LinearLayout statusCard=new LinearLayout(this);
        statusCard.setPadding(dp(12),dp(7),dp(12),dp(7));
        statusCard.setBackground(cardBg(GREEN2));
        status=statusView("جاهز — اضغط «ابدأ التسجيل» وتحدث بشكل طبيعي.");
        statusCard.addView(status,new LinearLayout.LayoutParams(0,dp(42),1));
        countView=tv("0 كلمة",12,Color.rgb(53,100,83));
        countView.setGravity(Gravity.CENTER);
        statusCard.addView(countView,new LinearLayout.LayoutParams(dp(75),dp(42)));
        root.addView(statusCard,new LinearLayout.LayoutParams(-1,dp(58)));

        LinearLayout rec=new LinearLayout(this);
        rec.setPadding(0,dp(6),0,dp(6));
        mic=btn("●  ابدأ التسجيل");
        stop=btn("■  إيقاف");
        mic.setTextColor(Color.WHITE); mic.setBackground(cardBg(GREEN));
        stop.setTextColor(DARK); stop.setBackground(cardBg(Color.rgb(222,235,230)));
        rec.addView(mic,new LinearLayout.LayoutParams(0,dp(58),1));
        rec.addView(stop,new LinearLayout.LayoutParams(0,dp(58),1));
        root.addView(rec);
        mic.setOnClickListener(v->startListening());
        stop.setOnClickListener(v->stopListening());

        LinearLayout liveBox=new LinearLayout(this);
        liveBox.setPadding(dp(12),dp(6),dp(12),dp(6));
        liveBox.setBackground(cardBg(Color.WHITE));
        liveView=tv("",12,Color.rgb(72,105,94));
        liveView.setText("النص الجاري التقاطه سيظهر هنا...");
        liveView.setTypeface(Typeface.DEFAULT,Typeface.ITALIC);
        liveBox.addView(liveView,new LinearLayout.LayoutParams(-1,dp(38)));
        root.addView(liveBox);

        editor=new EditText(this);
        editor.setTextSize(editorFontSize);
        editor.setTextColor(Color.rgb(25,35,31));
        editor.setGravity(Gravity.TOP|Gravity.RIGHT);
        editor.setHint("النص النهائي المصحح سيظهر هنا...");
        editor.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setPadding(dp(16),dp(16),dp(16),dp(16));
        editor.setBackground(cardBg(Color.WHITE));
        editor.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        editor.setOnTouchListener((v,e)->{ if(dynamicFont && scaleDetector!=null) scaleDetector.onTouchEvent(e); return false; });
        root.addView(editor,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout actions=new LinearLayout(this);
        Button save=btn("حفظ باسم"), word=btn("Word"), copy=btn("نسخ"), clear=btn("مسح"), dictionary=btn("قاموسي");
        for(Button x:new Button[]{save,word,copy,clear,dictionary}) actions.addView(x,new LinearLayout.LayoutParams(0,dp(48),1));
        root.addView(actions);
        save.setOnClickListener(v->saveCurrent());
        word.setOnClickListener(v->exportWord());
        copy.setOnClickListener(v->{((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(subject,editor.getText()));status.setText("تم نسخ النص.");});
        clear.setOnClickListener(v->{committedText="";liveText="";editor.setText("");liveView.setText("النص الجاري التقاطه سيظهر هنا...");updateCount();});
        dictionary.setOnClickListener(v->dictionaryDialog());
        setContentView(root);
    }

    
    void loadSettings(){
        showRecognitionChanges=prefs.getBoolean("show_changes",true);
        keepScreenOn=prefs.getBoolean("keep_screen",true);
        mutePhone=prefs.getBoolean("mute_phone",false);
        muteMedia=prefs.getBoolean("mute_media",false);
        showErrors=prefs.getBoolean("show_errors",true);
        longSpeech=prefs.getBoolean("long_speech",true);
        drawLines=prefs.getBoolean("draw_lines",false);
        dynamicFont=prefs.getBoolean("dynamic_font",false);
        mainButtonHeight=prefs.getInt("button_height",58);
        editorFontSize=prefs.getFloat("font_size",18f);
        if(editor!=null) editor.setTextSize(editorFontSize);
        if(scaleDetector==null) initScaleDetector();
        applyScreenSetting();
        applyAudioSettings();
        applyVisualSettings();
    }

    void initScaleDetector(){
        scaleDetector=new ScaleGestureDetector(this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            public boolean onScale(ScaleGestureDetector d){
                if(!dynamicFont)return false;
                editorFontSize=Math.max(14f,Math.min(32f,editorFontSize*d.getScaleFactor()));
                editor.setTextSize(editorFontSize);
                prefs.edit().putFloat("font_size",editorFontSize).apply();
                return true;
            }
        });
    }

    void applyScreenSetting(){
        if(keepScreenOn)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    void applyAudioSettings(){
        try{
            AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
            if(mutePhone){
                if(savedRing<0)savedRing=am.getStreamVolume(AudioManager.STREAM_RING);
                if(savedNotification<0)savedNotification=am.getStreamVolume(AudioManager.STREAM_NOTIFICATION);
                am.setStreamVolume(AudioManager.STREAM_RING,0,0);
                am.setStreamVolume(AudioManager.STREAM_NOTIFICATION,0,0);
            }else{
                if(savedRing>=0)am.setStreamVolume(AudioManager.STREAM_RING,savedRing,0);
                if(savedNotification>=0)am.setStreamVolume(AudioManager.STREAM_NOTIFICATION,savedNotification,0);
                savedRing=savedNotification=-1;
            }
            if(muteMedia){
                if(savedMusic<0)savedMusic=am.getStreamVolume(AudioManager.STREAM_MUSIC);
                am.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);
            }else{
                if(savedMusic>=0)am.setStreamVolume(AudioManager.STREAM_MUSIC,savedMusic,0);
                savedMusic=-1;
            }
        }catch(Exception e){}
    }

    void applyVisualSettings(){
        if(editor!=null){
            editor.setTextSize(editorFontSize);
            if(drawLines)editor.setBackground(new RuledDrawable(Color.WHITE,Color.rgb(220,230,225),dp(30)));
            else editor.setBackground(cardBg(Color.WHITE));
        }
        if(liveView!=null)liveView.setVisibility(showRecognitionChanges?View.VISIBLE:View.GONE);
        if(mic!=null){
            LinearLayout.LayoutParams a=(LinearLayout.LayoutParams)mic.getLayoutParams();
            if(a!=null){a.height=dp(mainButtonHeight);mic.setLayoutParams(a);}
        }
        if(stop!=null){
            LinearLayout.LayoutParams a=(LinearLayout.LayoutParams)stop.getLayoutParams();
            if(a!=null){a.height=dp(mainButtonHeight);stop.setLayoutParams(a);}
        }
    }

    Switch settingSwitch(LinearLayout box,String title,String summary,boolean value,CompoundButton.OnCheckedChangeListener listener){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10),dp(7),dp(10),dp(7));
        row.setBackground(cardBg(Color.WHITE));
        Switch sw=new Switch(this);
        sw.setText(title);
        sw.setTextSize(16);
        sw.setTextColor(DARK);
        sw.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        sw.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        sw.setChecked(value);
        TextView sub=tv(summary,11,Color.rgb(95,110,104));
        sub.setGravity(Gravity.RIGHT);
        row.addView(sw,new LinearLayout.LayoutParams(-1,dp(48)));
        row.addView(sub,new LinearLayout.LayoutParams(-1,dp(30)));
        box.addView(row,new LinearLayout.LayoutParams(-1,dp(88)));
        sw.setOnCheckedChangeListener(listener);
        return sw;
    }

    void settingsDialog(){
        ScrollView scroll=new ScrollView(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12),dp(6),dp(12),dp(18));
        box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.addView(box);

        TextView info=tv("إعدادات المخلافي صوت",22,GREEN);
        info.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(info,new LinearLayout.LayoutParams(-1,dp(58)));

        settingSwitch(box,"وضع التعرف المستمر","يستمر التعرف ويعيد تشغيل جلسة الاستماع تلقائيًا.",true,(b,v)->{});
        settingSwitch(box,"عرض تغييرات التعرف","إظهار/إخفاء النص الجاري التقاطه أثناء الكلام.",showRecognitionChanges,(b,v)->{
            showRecognitionChanges=v;prefs.edit().putBoolean("show_changes",v).apply();applyVisualSettings();
        });
        settingSwitch(box,"إبقاء الشاشة في وضع التشغيل","منع إطفاء الشاشة أثناء استخدام التطبيق.",keepScreenOn,(b,v)->{
            keepScreenOn=v;prefs.edit().putBoolean("keep_screen",v).apply();applyScreenSetting();
        });
        settingSwitch(box,"تعطيل أصوات الهاتف","كتم نغمة الرنين والإشعارات أثناء استخدام الإعداد.",mutePhone,(b,v)->{
            mutePhone=v;prefs.edit().putBoolean("mute_phone",v).apply();applyAudioSettings();
        });
        settingSwitch(box,"كتم صوت الوسائط","كتم صوت الوسائط أثناء استخدام التطبيق.",muteMedia,(b,v)->{
            muteMedia=v;prefs.edit().putBoolean("mute_media",v).apply();applyAudioSettings();
        });
        settingSwitch(box,"إظهار رسائل الخطأ","إظهار تفاصيل أخطاء التعرف بدل تجاهلها بصمت.",showErrors,(b,v)->{
            showErrors=v;prefs.edit().putBoolean("show_errors",v).apply();
        });
        settingSwitch(box,"الأمثل للكلام الطويل","رفع مهلة الصمت قبل إنهاء جلسة التعرف.",longSpeech,(b,v)->{
            longSpeech=v;prefs.edit().putBoolean("long_speech",v).apply();
        });
        settingSwitch(box,"تغيير حجم النص عن طريق لفتة التكبير","كبّر أو صغّر النص بإصبعين داخل مربع النص.",dynamicFont,(b,v)->{
            dynamicFont=v;prefs.edit().putBoolean("dynamic_font",v).apply();
        });

        TextView appearance=tv("المظهر",18,Color.rgb(90,100,105));
        appearance.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(appearance,new LinearLayout.LayoutParams(-1,dp(50)));
        Button theme=btn("السمة: "+prefs.getString("theme","فاتح"));
        box.addView(theme,new LinearLayout.LayoutParams(-1,dp(52)));
        theme.setOnClickListener(v->chooseTheme(theme));

        TextView fontTitle=tv("الخط",18,Color.rgb(90,100,105));
        fontTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(fontTitle,new LinearLayout.LayoutParams(-1,dp(50)));
        Button font=btn("نوع الخط: "+prefs.getString("font","افتراضي"));
        box.addView(font,new LinearLayout.LayoutParams(-1,dp(52)));
        font.setOnClickListener(v->chooseFont(font));

        TextView sizeTitle=tv("حجم الخط",18,Color.rgb(90,100,105));
        sizeTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(sizeTitle,new LinearLayout.LayoutParams(-1,dp(48)));
        SeekBar size=new SeekBar(this);
        size.setMax(18);size.setProgress((int)Math.max(0,Math.min(18,editorFontSize-14)));
        box.addView(size,new LinearLayout.LayoutParams(-1,dp(52)));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean from){editorFontSize=14+p;editor.setTextSize(editorFontSize);prefs.edit().putFloat("font_size",editorFontSize).apply();}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });

        settingSwitch(box,"ارسم خطوطًا","إظهار خطوط مساعدة داخل مساحة الكتابة.",drawLines,(b,v)->{
            drawLines=v;prefs.edit().putBoolean("draw_lines",v).apply();applyVisualSettings();
        });

        TextView buttonTitle=tv("حجم الزر الرئيسي",18,Color.rgb(90,100,105));
        buttonTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(buttonTitle,new LinearLayout.LayoutParams(-1,dp(48)));
        SeekBar bs=new SeekBar(this);bs.setMax(50);bs.setProgress(Math.max(0,Math.min(50,mainButtonHeight-40)));
        box.addView(bs,new LinearLayout.LayoutParams(-1,dp(52)));
        bs.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int p,boolean from){mainButtonHeight=40+p;prefs.edit().putInt("button_height",mainButtonHeight).apply();applyVisualSettings();}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });

        TextView rec=tv("السجل",18,Color.rgb(90,100,105));
        rec.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        box.addView(rec,new LinearLayout.LayoutParams(-1,dp(50)));
        Button saved=btn("حفظ المحفوظات على القرص\nعدد المحفوظات: "+prefs.getInt("history_limit",50));
        box.addView(saved,new LinearLayout.LayoutParams(-1,dp(60)));
        saved.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("حفظ المحفوظات على القرص")
            .setMessage("يحفظ التطبيق نصوص المواضيع تلقائيًا داخل بياناته المحلية. التسجيل الصوتي الخام يحتاج محرك التقاط مستقلًا عن SpeechRecognizer، لذلك لن أفعّل تسجيلًا صوتيًا وهميًا أو يقطع التعرف.")
            .setPositiveButton("حسنًا",null).show());

        new AlertDialog.Builder(this).setTitle("الإعدادات").setView(scroll).setPositiveButton("إغلاق",null).show();
    }

    void chooseTheme(Button b){
        String[] a={"فاتح","داكن","أخضر"};
        new AlertDialog.Builder(this).setTitle("السمة").setItems(a,(d,w)->{
            String t=a[w];prefs.edit().putString("theme",t).apply();
            if("داكن".equals(t)){rootView.setBackgroundColor(Color.rgb(25,30,28));editor.setTextColor(Color.WHITE);}
            else if("أخضر".equals(t)){rootView.setBackgroundColor(Color.rgb(236,248,242));editor.setTextColor(Color.rgb(20,45,35));}
            else {rootView.setBackgroundColor(LIGHT);editor.setTextColor(Color.rgb(25,35,31));}
            b.setText("السمة: "+t);
        }).show();
    }

    void chooseFont(Button b){
        String[] a={"افتراضي","نسخي","أحادي المسافة"};
        new AlertDialog.Builder(this).setTitle("الخط").setItems(a,(d,w)->{
            String t=a[w];prefs.edit().putString("font",t).apply();
            if(w==1)editor.setTypeface(Typeface.SERIF); else if(w==2)editor.setTypeface(Typeface.MONOSPACE); else editor.setTypeface(Typeface.DEFAULT);
            b.setText("نوع الخط: "+t);
        }).show();
    }

TextView statusView(String s){TextView v=tv(s,13,Color.rgb(25,70,55));v.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);return v;}

    void newSubject(boolean ask){
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12),0,dp(12),0);
        EditText name=new EditText(this); name.setSingleLine(true); name.setHint("مثال: موضوع القضية رقم 25");
        box.addView(name);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("موضوع جديد")
            .setMessage("اكتب اسم الموضوع. سيُستخدم الاسم في السجل واسم ملف Word.")
            .setView(box).setNegativeButton(ask?"إلغاء":"لاحقًا",null).setPositiveButton("فتح",null).create();
        d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{
            String n=name.getText().toString().trim();
            if(n.isEmpty()){name.setError("اكتب اسم الموضوع");return;}
            if(!subject.isEmpty()&&editor.length()>0)saveCurrent();
            subject=n; subjectView.setText("الموضوع: "+subject);
            committedText=""; liveText=""; editor.setText(""); liveView.setText("النص الجاري التقاطه سيظهر هنا...");
            addHistory(subject); status.setText("تم فتح الموضوع: "+subject); updateCount(); d.dismiss();
        }));
        d.setOnCancelListener(x->{if(subject.isEmpty()){subject="موضوع جديد";subjectView.setText("الموضوع: "+subject);status.setText("يمكنك تغيير الاسم من «موضوع جديد».");}});
        d.show();
    }

    void addHistory(String s){
        LinkedHashSet<String> set=new LinkedHashSet<>();
        String raw=prefs.getString("history","");
        if(!raw.isEmpty())set.addAll(Arrays.asList(raw.split("\\|")));
        set.remove(s);set.add(s);
        prefs.edit().putString("history",join(set,"|")).apply();
    }
    String join(Collection<String> c,String sep){StringBuilder b=new StringBuilder();for(String s:c){if(b.length()>0)b.append(sep);b.append(s.replace("|"," "));}return b.toString();}

    void showHistory(){
        String raw=prefs.getString("history",""); ArrayList<String> items=new ArrayList<>();
        if(!raw.isEmpty())for(String s:raw.split("\\|"))if(!s.trim().isEmpty())items.add(s);
        if(items.isEmpty()){new AlertDialog.Builder(this).setTitle("سجل المواضيع").setMessage("لا توجد مواضيع محفوظة بعد.").setPositiveButton("إغلاق",null).show();return;}
        Collections.reverse(items); String[] arr=items.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("سجل المواضيع").setItems(arr,(d,w)->openHistoryTopic(arr[w])).setNegativeButton("إغلاق",null).show();
    }

    void openHistoryTopic(String name){
        if(!subject.isEmpty()&&editor.length()>0)saveCurrent();
        subject=name;subjectView.setText("الموضوع: "+subject);
        String saved=prefs.getString("draft_"+safeKey(subject),"");
        committedText=saved;liveText="";editor.setText(saved);editor.setSelection(editor.length());
        liveView.setText("النص الجاري التقاطه سيظهر هنا...");status.setText("تم فتح الموضوع: "+subject);updateCount();
    }

    String safeKey(String s){return s.replaceAll("[/:*?\\\"<>|]","_");}
    void saveCurrent(){
        if(subject==null||subject.trim().isEmpty()){newSubject(true);return;}
        committedText=editor.getText().toString();
        prefs.edit().putString("draft_"+safeKey(subject),committedText).apply();
        addHistory(subject); status.setText("تم حفظ الموضوع باسم: "+subject);
    }

    void setupRecognizer(){
        if(!SpeechRecognizer.isRecognitionAvailable(this)){status.setText("التعرف الصوتي غير متاح على هذا الجهاز.");return;}
        sr=SpeechRecognizer.createSpeechRecognizer(this);
        sr.setRecognitionListener(new RecognitionListener(){
            public void onReadyForSpeech(Bundle x){status.setText("أستمع الآن — التسجيل مستمر.");}
            public void onBeginningOfSpeech(){status.setText("تحدث الآن...");}
            public void onRmsChanged(float x){}
            public void onBufferReceived(byte[] x){}
            public void onEndOfSpeech(){if(listening)status.setText("صمت مؤقت — التسجيل ما زال مستمرًا.");}
            public void onError(int e){
                liveText="";
                liveView.setText("");
                if(listening&&!destroying)handler.postDelayed(()->{if(listening&&!destroying)listenOnce();},120);
                if(!showErrors && !destroying) status.setText("التعرف مستمر.");
                else if(!destroying && !listening){mic.setText("●  ابدأ التسجيل");status.setText("تم إيقاف التسجيل.");}
            }
            public void onPartialResults(Bundle x){
                ArrayList<String>a=x.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(a!=null&&!a.isEmpty()){
                    liveText=cleanForDisplay(a.get(0));
                    if(showRecognitionChanges) liveView.setText("جاري الالتقاط: "+liveText);
                    renderLiveText();
                }
            }
            public void onResults(Bundle x){
                ArrayList<String>a=x.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if(a!=null&&!a.isEmpty())commitSegment(a.get(0));
                liveText="";liveView.setText("");
                if(listening&&!destroying)handler.postDelayed(()->{if(listening&&!destroying)listenOnce();},80);
            }
            public void onEvent(int x,Bundle y){}
        });
    }

    void startListening(){
        if(subject.isEmpty()||subject.equals("موضوع جديد")){newSubject(true);return;}
        if(sr==null){setupRecognizer();if(sr==null)return;}
        listening=true;mic.setText("●  التسجيل مستمر");status.setText("التسجيل مستمر — تحدث دون الحاجة لإيقافه.");
        listenOnce();
    }

    void listenOnce(){
        if(!listening||destroying||sr==null||starting)return;
        starting=true;
        try{
            Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ar-YE");
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"ar-YE");
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
            i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,false);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,longSpeech?120000L:65000L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,1000L);
            i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,longSpeech?110000L:60000L);
            sr.startListening(i);
        }catch(Exception e){
            if(listening)handler.postDelayed(()->listenOnce(),400);
        }finally{starting=false;}
    }

    String cleanForDisplay(String s){return normalize(processCommands(s));}

    void renderLiveText(){
        String base=committedText==null?"":committedText;
        String display=base;
        if(liveText!=null&&!liveText.trim().isEmpty()){
            if(display.isEmpty()) display=liveText;
            else if(!display.endsWith("\n")&&!startsPunctuation(liveText)) display+=" "+liveText;
            else display+=liveText;
        }
        editor.setText(display);
        editor.setSelection(editor.length());
        updateCount();
    }

    void commitSegment(String s){
        String o=cleanForDisplay(s);
        if(o.trim().isEmpty())return;
        if(committedText.isEmpty())committedText=o;
        else if(!committedText.endsWith("\n")&&!startsPunctuation(o))committedText+=" "+o;
        else committedText+=o;
        editor.setText(committedText);editor.setSelection(editor.length());updateCount();
        status.setText("تم التقاط النص وتصحيحه — التسجيل مستمر.");
    }

    boolean startsPunctuation(String s){return s.startsWith(")")||s.startsWith("،")||s.startsWith(".")||s.startsWith(":")||s.startsWith("؟")||s.startsWith("!")||s.startsWith("؛")||s.startsWith("”");}

    void stopListening(){
        listening=false;liveText="";liveView.setText("");
        if(sr!=null)sr.stopListening();
        mic.setText("●  ابدأ التسجيل");status.setText("تم إيقاف التسجيل.");
        saveCurrent();
    }

    String processCommands(String s){
        s=s.replaceAll("(?i)(افتح|فتح|ضع|حط|اعمل|سوي)\\s+(قوس|القوس)","(");
        s=s.replaceAll("(?i)(اغلق|أغلق|سكر|اقفل|أقفل)\\s*(قوس|القوس|ه)?",")");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(فاصلة|فاصله)","،");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(نقطة|نقطه)"," . ");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(علامة\\s*استفهام|علامة\\s*السؤال|استفهام|سؤال)","؟");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(علامة\\s*تعجب|علامة\\s*التعجب|تعجب)","!");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(نقطتين|نقطتين\\s+فوق\\s+بعض|نقطتان)",":");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(فاصلة\\s*منقوطة|فاصله\\s*منقوطه|منقوطة)","؛");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(شرطة|خط)","-");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(سطر\\s*جديد|انزل سطر|فقرة جديدة)","\n");
        s=s.replaceAll("(?i)(افتح|فتح)\\s+(اقتباس|علامة اقتباس)","“");
        s=s.replaceAll("(?i)(اغلق|أغلق|سكر|اقفل|أقفل)\\s+(اقتباس|علامة اقتباس)","”");
        Matcher m=Pattern.compile("فقرة\\s+(?:رقم\\s*)?([0-9٠-٩]+)").matcher(s);
        StringBuffer z=new StringBuffer();
        while(m.find())m.appendReplacement(z,Matcher.quoteReplacement(".\n"+western(m.group(1))+"/"));
        m.appendTail(z);s=z.toString();
        s=s.replaceAll("(?i)فقرة\\s+(?:الف|ألف|الألف)",".\nأ/");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(تنوين\\s*فتح|تنوين\\s*بالفتح)","__TAN_FATHA__");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(تنوين\\s*ضم|تنوين\\s*بالضم)","__TAN_DAMMA__");
        s=s.replaceAll("(?i)(ضع|حط|اعمل|سوي)?\\s*(تنوين\\s*كسر|تنوين\\s*بالكسر)","__TAN_KASRA__");
        return s;
    }

    String normalize(String s){
        String[][]p={
            {"الاستناف","الاستئناف"},{"الاستأناف","الاستئناف"},{"المحكمه","المحكمة"},{"القضيه","القضية"},{"الدعوي","الدعوى"},
            {"مسوليه","مسؤولية"},{"مسؤوليه","مسؤولية"},{"مسئولية","مسؤولية"},{"هيئه","هيئة"},{"المحاماه","المحاماة"},
            {"اثبات","إثبات"},{"الاثبات","الإثبات"},{"المساله","المسألة"},{"مساله","مسألة"},{"الاجراءات","الإجراءات"},
            {"اجراءات","إجراءات"},{"الاحكام","الأحكام"},{"احكام","أحكام"},{"الادله","الأدلة"},{"ادله","أدلة"},
            {"المبرره","المبررة"},{"المقدمه","المقدمة"},{"المستأنفه","المستأنفة"},{"القاضيه","القاضية"},{"المدعيه","المدعية"},
            {"المدعاه","المدعاة"},{"قضيه","قضية"},{"دعوي","دعوى"},{"هيئه","هيئة"},{"المسؤوليه","المسؤولية"},{"مسوولية","مسؤولية"},
            {"المساله","المسألة"},{"الوثيقه","الوثيقة"},{"النيابه","النيابة"},{"الاحاله","الإحالة"},{"الاحوال","الأحوال"},
            {"الاقرار","الإقرار"},{"اقرار","إقرار"},{"الاستئناف","الاستئناف"},{"المحاماة","المحاماة"},{"المحكمة","المحكمة"}
        };
        for(String[]x:p)s=s.replace(x[0],x[1]);
        for(Map.Entry<String,String>x:dict.entrySet())s=s.replace(x.getKey(),x.getValue());
        s=correctArabicWords(s);
        s=applyTanwin(s);
        return s.replaceAll("\\s+([،.:؟!؛)])","$1").replaceAll("\\(\\s+","(").replaceAll("\\s+([”])","$1").trim();
    }

    String correctArabicWords(String s){
        String[][]w={
            {"كلمه","كلمة"},{"التاء المربوطه","التاء المربوطة"},{"الهيئه","الهيئة"},{"هيئه","هيئة"},
            {"الاداره","الإدارة"},{"اداره","إدارة"},{"الرئاسه","الرئاسة"},{"رئاسه","رئاسة"},
            {"المحكمه","المحكمة"},{"محكمه","محكمة"},{"المحاكمه","المحاكمة"},{"محاكمه","محاكمة"},
            {"القضيه","القضية"},{"قضيه","قضية"},{"الدعوي","الدعوى"},{"دعوي","دعوى"},
            {"الدعاوي","الدعاوى"},{"المدعيه","المدعية"},{"المستأنفه","المستأنفة"},
            {"المسئوليه","المسؤولية"},{"مسئوليه","مسؤولية"},{"مسوليه","مسؤولية"},{"مسؤوليه","مسؤولية"},
            {"المسئول","المسؤول"},{"مسئول","مسؤول"},{"المساله","المسألة"},{"مساله","مسألة"},
            {"الاجراء","الإجراء"},{"اجراء","إجراء"},{"الاجراءات","الإجراءات"},{"اجراءات","إجراءات"},
            {"الاحاله","الإحالة"},{"احاله","إحالة"},{"الاحكام","الأحكام"},{"احكام","أحكام"},
            {"الاقرار","الإقرار"},{"اقرار","إقرار"},{"الاستناف","الاستئناف"},{"الاثبات","الإثبات"},
            {"اثبات","إثبات"},{"الادله","الأدلة"},{"ادله","أدلة"},{"الاوراق","الأوراق"},{"اوراق","أوراق"},
            {"الاسباب","الأسباب"},{"اسباب","أسباب"},{"الواقعه","الواقعة"},{"واقعه","واقعة"},
            {"المذكره","المذكرة"},{"مذكره","مذكرة"},{"المحاماه","المحاماة"},{"محاماه","محاماة"},
            {"النيابه","النيابة"},{"نيابه","نيابة"},{"الوكاله","الوكالة"},{"وكاله","وكالة"},
            {"الوثيقه","الوثيقة"},{"وثيقه","وثيقة"},{"الاضرار","الأضرار"},{"اضرار","أضرار"},
            {"المطالبه","المطالبة"},{"مطالبه","مطالبة"},{"الاحقيه","الأحقية"},{"احقيه","أحقية"},
            {"الشرعيه","الشرعية"},{"شرعيه","شرعية"},{"القانونيه","القانونية"},{"قانونيه","قانونية"},
            {"الاصليه","الأصلية"},{"اصليه","أصلية"},{"الفرعيه","الفرعية"},{"فرعيه","فرعية"},
            {"النهائيه","النهائية"},{"نهائيه","نهائية"},{"الابتدائيه","الابتدائية"},{"ابتدائيه","ابتدائية"},
            {"المدنيه","المدنية"},{"مدنيه","مدنية"},{"الجزائيه","الجزائية"},{"جزائيه","جزائية"},
            {"التجاريه","التجارية"},{"تجاريه","تجارية"},{"الاسره","الأسرة"},{"اسره","أسرة"},
            {"لان","لأن"},{"لانها","لأنها"},{"لانه","لأنه"},{"الى","إلى"},{"ان","أن"},{"انها","أنها"},
            {"انه","أنه"},{"اخر","آخر"},{"اخرى","أخرى"},{"اولا","أولًا"},{"ثانيا","ثانيًا"},
            {"ثالثا","ثالثًا"},{"رابعا","رابعًا"},{"خامسا","خامسًا"},{"اثناء","أثناء"},
            {"اساس","أساس"},{"اهمية","أهمية"},{"اوضح","أوضح"},{"اقامه","إقامة"},{"اقامة","إقامة"},
            {"ادانه","إدانة"},{"ادانة","إدانة"},{"ابراء","إبراء"},{"استنادا","استنادًا"},
            {"بناءا","بناءً"},{"وفقا","وفقًا"}
        };
        for(String[]x:w)s=s.replace(x[0],x[1]);
        return s;
    }

    String applyTanwin(String s){
        s=s.replace("__TAN_FATHA__","").replace("__TAN_DAMMA__","").replace("__TAN_KASRA__","");
        String[][]w={
            {"تماما","تمامًا"},{"فعلا","فعلًا"},{"غالبا","غالبًا"},{"عادة","عادةً"},{"خصوصا","خصوصًا"},{"مبدئيا","مبدئيًا"},
            {"نهائيا","نهائيًا"},{"قانونا","قانونًا"},{"شرعا","شرعًا"},{"حكما","حكمًا"},{"بناءا","بناءً"},{"وفقا","وفقًا"},
            {"استنادا","استنادًا"},{"تبعا","تبعًا"},{"صراحة","صراحةً"},{"ابتداءا","ابتداءً"},{"وانتهاءا","وانتهاءً"},
            {"مثلا","مثلًا"},{"إجمالا","إجمالًا"},{"تفصيلا","تفصيلًا"},{"خصوصاً","خصوصًا"},{"فعلاً","فعلًا"}
        };
        for(String[]x:w)s=s.replaceAll("(?<![\\u0627-\\u064A])"+Pattern.quote(x[0])+"(?![\\u0627-\\u064A])",x[1]);
        return s;
    }

    String western(String s){return s.replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4').replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9');}

    void dictionaryDialog(){
        LinearLayout x=new LinearLayout(this);x.setOrientation(LinearLayout.VERTICAL);x.setPadding(dp(14),0,dp(14),0);
        EditText a=new EditText(this);a.setHint("النطق أو الكلمة التي تظهر خطأ");
        EditText b=new EditText(this);b.setHint("الكتابة الصحيحة");
        x.addView(a);x.addView(b);
        AlertDialog d=new AlertDialog.Builder(this).setTitle("قاموس المخلافي")
            .setMessage("أضف مصطلحات يمنية أو قضائية خاصة بك ليتم تصحيحها تلقائيًا.")
            .setView(x).setNegativeButton("إغلاق",null).setPositiveButton("حفظ",null).create();
        d.setOnShowListener(v->d.getButton(-1).setOnClickListener(q->{String aa=a.getText().toString().trim(),bb=b.getText().toString().trim();if(!aa.isEmpty()&&!bb.isEmpty()){dict.put(aa,bb);saveDictionary();status.setText("تم حفظ المصطلح في قاموس المخلافي.");d.dismiss();}}));
        d.show();
    }

    void loadDictionary(){String s=prefs.getString("dictionary","");if(!s.isEmpty())for(String q:s.split("\\|")){int k=q.indexOf("=>");if(k>0)dict.put(q.substring(0,k),q.substring(k+2));}}
    void saveDictionary(){prefs.edit().putString("dictionary",joinDict()).apply();}
    String joinDict(){StringBuilder s=new StringBuilder();for(Map.Entry<String,String>x:dict.entrySet()){if(s.length()>0)s.append("|");s.append(x.getKey().replace("|"," ")).append("=>").append(x.getValue().replace("|"," "));}return s.toString();}

    void updateCount(){
        String s=editor==null?"":editor.getText().toString().trim();
        int n=s.isEmpty()?0:s.split("\\s+").length;
        countView.setText(n+" كلمة");
    }

    void exportWord(){
        String x=editor.getText().toString().trim();
        if(x.isEmpty()){status.setText("لا يوجد نص للتصدير.");return;}
        if(subject.isEmpty()){newSubject(true);return;}
        pendingHtml="<html><head><meta charset='UTF-8'></head><body dir='rtl' style='font-family:Arial;line-height:1.9;font-size:16pt'><h2 style='text-align:center'>"+esc(subject)+"</h2>"+esc(x).replace("\n","<br>")+"</body></html>";
        Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/msword");i.putExtra(Intent.EXTRA_TITLE,safeFileName(subject)+".doc");startActivityForResult(i,88);
    }

    String safeFileName(String s){String x=s.replaceAll("[\\\\/:*?\"<>|]","_").trim();return x.isEmpty()?"موضوع":x;}
    protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==88&&c==RESULT_OK&&d!=null)try{
            java.io.OutputStream o=getContentResolver().openOutputStream(d.getData());o.write(pendingHtml.getBytes("UTF-8"));o.close();saveCurrent();status.setText("تم تصدير Word باسم الموضوع: "+subject);
        }catch(Exception e){status.setText("تعذر حفظ ملف Word.");}
    }
    String esc(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;");}

    @Override protected void onStart(){
        super.onStart();
        appInForeground=true;
    }

    @Override protected void onStop(){
        super.onStop();
        appInForeground=false;
        if(!isChangingConfigurations() && listening){
            stopListening();
        }
    }

    @Override protected void onDestroy(){
        destroying=true;
        listening=false;
        if(sr!=null)sr.destroy();
        super.onDestroy();
    }

    static class RuledDrawable extends Drawable{
        Paint bg=new Paint(1), line=new Paint(1); int spacing;
        RuledDrawable(int background,int lineColor,int spacing){bg.setColor(background);line.setColor(lineColor);line.setStrokeWidth(1);this.spacing=spacing;}
        public void draw(Canvas c){c.drawRect(getBounds(),bg);for(int y=getBounds().top+spacing;y<getBounds().bottom;y+=spacing)c.drawLine(getBounds().left,y,getBounds().right,y,line);}
        public void setAlpha(int a){bg.setAlpha(a);line.setAlpha(a);}
        public void setColorFilter(android.graphics.ColorFilter f){bg.setColorFilter(f);line.setColorFilter(f);}
        public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }

    static class GradientDrawableBox extends android.graphics.drawable.GradientDrawable{
        GradientDrawableBox(int color,int radius){setColor(color);setCornerRadius(radius);}
    }
}