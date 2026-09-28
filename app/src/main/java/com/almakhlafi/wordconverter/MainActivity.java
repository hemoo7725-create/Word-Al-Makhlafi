package com.almakhlafi.wordconverter;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.googlecode.tesseract.android.ResultIterator;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public class MainActivity extends AppCompatActivity {
    static final int PICK=100, CREATE=200, CAMERA=300, TREE=400;
    LinearLayout list, root;
    TextView status;
    MaterialCardView selectedCard;
    LinearProgressIndicator progress;
    ArrayList<Uri> selected=new ArrayList<>();
    File pendingOutput;
    SharedPreferences prefs;
    int selectedPdfStartPage=1;
    int selectedPdfEndPage=-1;
    boolean pageSelectionReady=false;

    @Override public void onCreate(Bundle b){
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        applyTheme();
        super.onCreate(b);
        setContentView(ui());
    }

    void applyTheme(){
        boolean dark=prefs!=null && prefs.getBoolean("dark",false);
        AppCompatDelegate.setDefaultNightMode(dark?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);
    }

    int bg(){return isDark()?Color.rgb(18,30,24):Color.rgb(246,251,248);}
    int text(){return isDark()?Color.WHITE:Color.rgb(28,45,36);}
    boolean isDark(){return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}
    int green(){return Color.rgb(24,154,112);}
    int greenDark(){return Color.rgb(13,112,78);}
    int greenLight(){return Color.rgb(66,190,146);}

    int dp(float v){ return Math.round(v*getResources().getDisplayMetrics().density); }

    View ui(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg());
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(10),dp(8),dp(10),dp(14));
        page.setBackgroundColor(bg());

        // ===== واجهة المستخدم حسب التصميم المرسل =====
        MaterialCardView hero=card();
        hero.setCardBackgroundColor(greenDark());
        hero.setStrokeWidth(0);
        TextView title=new TextView(this);
        title.setText("W-المخلافي");
        title.setTextColor(Color.WHITE);
        title.setTextSize(27);
        title.setTypeface(null,Typeface.BOLD);
        title.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        title.setSingleLine(true);
        title.setPadding(dp(10),0,dp(12),0);
        hero.addView(title,new LinearLayout.LayoutParams(-1,dp(68)));
        page.addView(hero,new LinearLayout.LayoutParams(-1,dp(76)));

        MaterialCardView salawatCard=card();
        salawatCard.setBackground(gradient(Color.rgb(82,210,154),Color.rgb(8,105,70),dp(22)));
        salawatCard.setStrokeColor(Color.rgb(180,246,217));
        salawatCard.setStrokeWidth(dp(1));
        salawatCard.setCardElevation(dp(7));
        TextView salawat=new TextView(this);
        salawat.setText("ﷺ  صلِّ على محمد صلى الله عليه وسلم وعلى آله\nالطيبين الطاهرين  ﷺ");
        salawat.setTextColor(Color.WHITE);
        salawat.setTextSize(20);
        salawat.setTypeface(null,Typeface.BOLD);
        salawat.setGravity(Gravity.CENTER);
        salawat.setLineSpacing(0,1.0f);
        salawat.setShadowLayer(dp(2),0,dp(2),Color.argb(120,0,50,30));
        salawat.setPadding(dp(8),dp(8),dp(8),dp(8));
        salawatCard.addView(salawat,new LinearLayout.LayoutParams(-1,dp(112)));
        page.addView(salawatCard,new LinearLayout.LayoutParams(-1,dp(124)));

        MaterialCardView banner=card();
        banner.setBackground(gradient(Color.rgb(73,174,239),Color.rgb(25,106,184),dp(22)));
        banner.setStrokeColor(Color.rgb(148,215,250));
        banner.setStrokeWidth(dp(1));
        banner.setCardElevation(dp(6));
        TextView bannerText=new TextView(this);
        bannerText.setText("تحويل واستخراج جميع\nأنواع الملفات إلى وورد");
        bannerText.setTextColor(Color.WHITE);
        bannerText.setTextSize(23);
        bannerText.setTypeface(null,Typeface.BOLD);
        bannerText.setGravity(Gravity.CENTER);
        bannerText.setLineSpacing(0,0.95f);
        bannerText.setShadowLayer(dp(2),0,dp(2),Color.argb(150,0,55,100));
        banner.addView(bannerText,new LinearLayout.LayoutParams(-1,dp(104)));
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,dp(108));
        bp.setMargins(0,dp(12),0,dp(12));
        page.addView(banner,bp);

        selectedCard=card();
        selectedCard.setCardBackgroundColor(Color.WHITE);
        selectedCard.setStrokeColor(Color.rgb(205,225,215));
        selectedCard.setStrokeWidth(dp(1));
        selectedCard.setCardElevation(dp(2));

        LinearLayout filesBox=new LinearLayout(this);
        filesBox.setOrientation(LinearLayout.VERTICAL);
        filesBox.setPadding(dp(12),dp(8),dp(12),dp(8));

        TextView fh=sectionHeader("الملفات المحددة");
        fh.setTextSize(22);
        filesBox.addView(fh,new LinearLayout.LayoutParams(-1,dp(52)));

        LinearLayout row1=new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER);

        MaterialCardView importCard=actionCard("استيراد ملف","ic_import_file");
        MaterialCardView cameraCard=actionCard("التقاط صورة","ic_camera");
        importCard.setOnClickListener(v->pick());
        cameraCard.setOnClickListener(v->camera());

        LinearLayout.LayoutParams p1=new LinearLayout.LayoutParams(0,dp(170),1);
        p1.setMargins(dp(7),dp(6),dp(7),dp(10));
        LinearLayout.LayoutParams p2=new LinearLayout.LayoutParams(0,dp(170),1);
        p2.setMargins(dp(7),dp(6),dp(7),dp(10));
        row1.addView(importCard,p1);
        row1.addView(cameraCard,p2);
        filesBox.addView(row1);

        LinearLayout row2=new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.setGravity(Gravity.CENTER);

        MaterialCardView wordCard=actionCard("التحويل إلى ورد","ic_word");
        MaterialCardView settingsCard=actionCard("الإعدادات","ic_settings");
        wordCard.setOnClickListener(v->createOutput());
        settingsCard.setOnClickListener(v->settingsDialog());

        LinearLayout.LayoutParams p3=new LinearLayout.LayoutParams(0,dp(170),1);
        p3.setMargins(dp(7),dp(6),dp(7),dp(10));
        LinearLayout.LayoutParams p4=new LinearLayout.LayoutParams(0,dp(170),1);
        p4.setMargins(dp(7),dp(6),dp(7),dp(10));
        row2.addView(wordCard,p3);
        row2.addView(settingsCard,p4);
        filesBox.addView(row2);

        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(4),dp(4),dp(4),dp(4));
        filesBox.addView(list,new LinearLayout.LayoutParams(-1,dp(90)));

        TextView empty=sectionHeader("تم مسح القائمة.");
        empty.setTextSize(17);
        empty.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        filesBox.addView(empty,new LinearLayout.LayoutParams(-1,dp(38)));

        selectedCard.addView(filesBox);
        page.addView(selectedCard,new LinearLayout.LayoutParams(-1,dp(600)));

        status=new TextView(this);
        status.setText("جاهز");
        status.setTextColor(greenDark());
        status.setTextSize(13);
        status.setTypeface(null,Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setVisibility(View.GONE);
        page.addView(status,new LinearLayout.LayoutParams(-1,dp(34)));

        progress=new LinearProgressIndicator(this);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        page.addView(progress,new LinearLayout.LayoutParams(-1,dp(4)));

        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        return root;
    }

    MaterialCardView actionCard(String label,String iconName){
        MaterialCardView c=new MaterialCardView(this);
        c.setRadius(dp(18));
        c.setCardBackgroundColor(Color.rgb(92,50,180));
        c.setStrokeWidth(0);
        c.setCardElevation(dp(3));
        c.setUseCompatPadding(false);

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(6),dp(10),dp(6),dp(8));

        ImageView icon=new ImageView(this);
        int res=getResources().getIdentifier(iconName,"drawable",getPackageName());
        icon.setImageResource(res);
        icon.setColorFilter(Color.WHITE,android.graphics.PorterDuff.Mode.SRC_IN);
        icon.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        box.addView(icon,new LinearLayout.LayoutParams(-1,dp(88)));

        TextView t=new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(19);
        t.setTypeface(null,Typeface.BOLD);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(false);
        box.addView(t,new LinearLayout.LayoutParams(-1,dp(54)));

        c.addView(box,new MaterialCardView.LayoutParams(-1,-1));
        return c;
    }

    MaterialCardView dashboardCard(String icon,String title,String sub,int fill,int accent){
        MaterialCardView c=card();
        c.setCardBackgroundColor(fill);
        c.setStrokeColor(Color.argb(45,Color.red(accent),Color.green(accent),Color.blue(accent)));
        c.setStrokeWidth(dp(1));
        c.setCardElevation(dp(2));
        LinearLayout b=new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(5),dp(8),dp(5),dp(6));

        TextView i=new TextView(this);
        i.setText(icon);
        i.setTextSize(34);
        i.setTypeface(null,Typeface.BOLD);
        i.setTextColor(accent);
        i.setGravity(Gravity.CENTER);
        i.setBackground(gradient(Color.WHITE,Color.argb(35,Color.red(accent),Color.green(accent),Color.blue(accent)),dp(32)));
        b.addView(i,new LinearLayout.LayoutParams(dp(68),dp(68)));

        TextView t=new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(null,Typeface.BOLD);
        t.setTextColor(accent==Color.rgb(226,153,18)?Color.rgb(75,48,5):Color.rgb(22,46,92));
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setPadding(0,dp(7),0,0);
        b.addView(t,new LinearLayout.LayoutParams(-1,dp(36)));

        TextView s=new TextView(this);
        s.setText(sub);
        s.setTextSize(10);
        s.setTextColor(Color.rgb(83,101,113));
        s.setGravity(Gravity.CENTER);
        b.addView(s,new LinearLayout.LayoutParams(-1,dp(48)));
        c.addView(b);
        return c;
    }

    MaterialButton smallHeaderButton(String s){
        MaterialButton b=new MaterialButton(this);
        b.setText(s);
        b.setTextSize(19);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        b.setCornerRadius(18);
        b.setBackgroundColor(Color.TRANSPARENT);
        b.setInsetTop(0);b.setInsetBottom(0);
        return b;
    }

    void showMorePopup(View anchor){
        PopupMenu p=new PopupMenu(this,anchor);
        p.getMenu().add("📁 مكان الحفظ");
        p.getMenu().add("🧹 مسح الذاكرة المؤقتة");
        p.getMenu().add("ℹ معلومات التطبيق");
        p.setOnMenuItemClickListener(i->{
            String s=i.getTitle().toString();
            if(s.contains("مكان الحفظ"))chooseSaveFolder();
            else if(s.contains("مسح"))clearCache();
            else infoDialog();
            return true;
        });
        p.show();
    }

    MaterialCardView featureCard(String icon,String title,String sub){
        MaterialCardView c=card();
        c.setCardBackgroundColor(Color.rgb(240,252,247));
        c.setStrokeColor(Color.rgb(218,239,229));
        c.setStrokeWidth(1);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(9,7,9,7);
        TextView ic=new TextView(this);
        ic.setText(icon);
        ic.setTextSize(27);
        ic.setGravity(Gravity.CENTER);
        ic.setTextColor(greenDark());
        ic.setBackground(gradient(Color.rgb(22,184,125),Color.rgb(30,165,116),45));
        box.addView(ic,new LinearLayout.LayoutParams(54,54));
        LinearLayout tx=new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        TextView t=new TextView(this);t.setText(title);t.setTextSize(16);t.setTypeface(null,Typeface.BOLD);t.setTextColor(text());t.setGravity(Gravity.RIGHT);
        TextView s=new TextView(this);s.setText(sub);s.setTextSize(10);s.setTextColor(greenDark());s.setGravity(Gravity.RIGHT);
        tx.addView(t);tx.addView(s);
        box.addView(tx,new LinearLayout.LayoutParams(0,-1,1));
        TextView arrow=new TextView(this);arrow.setText("‹");arrow.setTextSize(32);arrow.setTextColor(greenDark());arrow.setGravity(Gravity.CENTER);
        box.addView(arrow,new LinearLayout.LayoutParams(30,-1));
        c.addView(box);
        return c;
    }

    MaterialButton greenOutlineButton(String s){
        MaterialButton b=new MaterialButton(this);
        b.setText(s);b.setTextSize(18);b.setTypeface(null,Typeface.BOLD);b.setAllCaps(false);
        b.setTextColor(greenDark());b.setGravity(Gravity.CENTER);b.setCornerRadius(18);
        b.setStrokeWidth(1);b.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.rgb(210,232,221)));
        b.setBackground(gradient(Color.WHITE,Color.rgb(239,250,245),18));b.setInsetTop(0);b.setInsetBottom(0);
        return b;
    }

    LinearLayout referencePanel(){
        LinearLayout p=new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(5,4,5,4);
        p.setBackground(gradient(Color.WHITE,Color.rgb(247,252,249),16));
        return p;
    }

    void addReferenceItem(LinearLayout panel,String icon,String label,View.OnClickListener click){
        LinearLayout row=new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(5,0,4,0);
        TextView ic=new TextView(this);ic.setText(icon);ic.setTextSize(21);ic.setTextColor(greenDark());ic.setGravity(Gravity.CENTER);
        row.addView(ic,new LinearLayout.LayoutParams(38,48));
        TextView t=new TextView(this);t.setText(label);t.setTextSize(13);t.setTextColor(text());t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        row.addView(t,new LinearLayout.LayoutParams(0,48,1));
        TextView a=new TextView(this);a.setText("‹");a.setTextSize(25);a.setTextColor(Color.GRAY);a.setGravity(Gravity.CENTER);
        row.addView(a,new LinearLayout.LayoutParams(25,48));
        row.setOnClickListener(click);
        panel.addView(row);
        View line=new View(this);line.setBackgroundColor(Color.rgb(232,238,234));
        panel.addView(line,new LinearLayout.LayoutParams(-1,1));
    }

    TextView sectionHeader(String s){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(21);t.setTypeface(null,Typeface.BOLD);t.setTextColor(greenDark());t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);t.setPadding(6,4,6,4);return t;
    }

    void addQuick(LinearLayout parent,String icon,String title,String sub,View.OnClickListener click){
        MaterialCardView c=card();c.setCardBackgroundColor(Color.rgb(239,252,246));c.setStrokeColor(Color.rgb(214,240,226));c.setStrokeWidth(1);c.setOnClickListener(click);
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setGravity(Gravity.CENTER);b.setPadding(5,6,5,5);
        TextView i=new TextView(this);i.setText(icon);i.setTextSize(29);i.setGravity(Gravity.CENTER);
        TextView t=new TextView(this);t.setText(title);t.setTextSize(12);t.setTypeface(null,Typeface.BOLD);t.setTextColor(text());t.setGravity(Gravity.CENTER);
        TextView q=new TextView(this);q.setText(sub);q.setTextSize(10);q.setTextColor(greenDark());q.setGravity(Gravity.CENTER);
        b.addView(i);b.addView(t);b.addView(q);c.addView(b);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,132,1);p.setMargins(3,0,3,0);parent.addView(c,p);
    }

    void addRecentPlaceholder(LinearLayout parent,String label){
        MaterialCardView c=card();c.setCardBackgroundColor(Color.WHITE);c.setStrokeColor(Color.rgb(225,236,230));c.setStrokeWidth(1);
        LinearLayout b=new LinearLayout(this);b.setOrientation(LinearLayout.VERTICAL);b.setGravity(Gravity.CENTER);
        TextView preview=new TextView(this);preview.setText("▤\\n▤\\n▤");preview.setTextSize(19);preview.setGravity(Gravity.CENTER);preview.setTextColor(Color.rgb(155,180,166));preview.setBackgroundColor(Color.rgb(246,251,248));
        TextView n=new TextView(this);n.setText(label);n.setTextSize(12);n.setTextColor(text());n.setGravity(Gravity.CENTER);
        TextView date=new TextView(this);date.setText("جاهز");date.setTextSize(10);date.setTextColor(Color.GRAY);date.setGravity(Gravity.CENTER);
        b.addView(preview,new LinearLayout.LayoutParams(-1,72));b.addView(n);b.addView(date);c.addView(b);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,137,1);p.setMargins(3,0,3,0);parent.addView(c,p);
    }

    void addNav(LinearLayout nav,String icon,String label,View.OnClickListener click){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);item.setOnClickListener(click);
        TextView i=new TextView(this);i.setText(icon);i.setTextSize(25);i.setTextColor(Color.rgb(88,99,108));i.setGravity(Gravity.CENTER);
        TextView t=new TextView(this);t.setText(label);t.setTextSize(11);t.setTextColor(Color.rgb(88,99,108));t.setGravity(Gravity.CENTER);t.setTypeface(null,Typeface.BOLD);
        item.addView(i,new LinearLayout.LayoutParams(-1,34));item.addView(t,new LinearLayout.LayoutParams(-1,30));
        nav.addView(item,new LinearLayout.LayoutParams(0,70,1));
    }

    MaterialButton greenMainButton(String s){
        MaterialButton b=new MaterialButton(this);b.setText(s);b.setTextSize(17);b.setTypeface(null,Typeface.BOLD);b.setAllCaps(false);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER);b.setCornerRadius(20);b.setStrokeWidth(0);b.setBackground(gradient(Color.rgb(7,126,82),Color.rgb(25,164,112),20));b.setInsetTop(0);b.setInsetBottom(0);return b;
    }

    MaterialButton actionButton(String s,boolean unused){return greenMainButton(s);}
    MaterialButton purpleMenuButton(String s){return greenOutlineButton(s);}
    LinearLayout dropdownPanel(){return referencePanel();}
    void addMenuItem(LinearLayout panel,String label,View.OnClickListener click){addReferenceItem(panel,"•",label,click);}
    MaterialButton bigGreenButton(String s){return greenMainButton(s);}
    MaterialButton menuButton(String s){return greenOutlineButton(s);}

    MaterialCardView card(){
        MaterialCardView c=new MaterialCardView(this);
        c.setRadius(18);
        c.setCardElevation(2);
        c.setUseCompatPadding(true);
        c.setContentPadding(0,0,0,0);
        return c;
    }

    android.graphics.drawable.GradientDrawable gradient(int top,int bottom,int radius){
        android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            new int[]{top,bottom});
        g.setCornerRadius(radius);
        return g;
    }

    TextView sectionTitle(String s){
        TextView t=new TextView(this);
        t.setText(s);
        t.setTextSize(21);
        t.setTypeface(null,Typeface.BOLD);
        t.setTextColor(greenDark());
        t.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        t.setPadding(6,4,6,4);
        return t;
    }

    void conversionMenu(View anchor){
        PopupMenu p=new PopupMenu(this,anchor);
        p.getMenu().add("PDF إلى Word — كل الصفحات");
        p.getMenu().add("PDF ممسوح ضوئيًا إلى Word");
        p.getMenu().add("صور إلى Word");
        p.getMenu().add("Excel إلى Word");
        p.getMenu().add("Word إلى Word");
        p.getMenu().add("PowerPoint إلى Word");
        p.getMenu().add("تحويل عدة ملفات دفعة واحدة");
        p.setOnMenuItemClickListener(i->{String s=i.getTitle().toString();if(s.startsWith("صور"))pickImages();else if(s.startsWith("PDF"))pickPdf();else pick();return true;});
        p.show();
    }

    void moreMenu(View anchor){
        PopupMenu p=new PopupMenu(this,anchor);
        p.getMenu().add("📚 الحافظة");
        p.getMenu().add("📁 مكان الحفظ");
        p.getMenu().add("⚙ الإعدادات");
        p.getMenu().add("🧹 مسح التخزين المؤقت");
        p.getMenu().add("ℹ معلومات التطبيق");
        p.setOnMenuItemClickListener(i->{String s=i.getTitle().toString();if(s.contains("الحافظة"))showLibrary();else if(s.contains("مكان الحفظ"))chooseSaveFolder();else if(s.contains("الإعدادات"))settingsDialog();else if(s.contains("مسح التخزين"))clearCache();else infoDialog();return true;});
        p.show();
    }

    void settingsDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(22,6,22,6);
        TextView h=sectionTitle("إعدادات W-المخلافي");box.addView(h);
        Switch dark=new Switch(this);dark.setText("الوضع الداكن");dark.setTextSize(16);dark.setGravity(Gravity.RIGHT);dark.setChecked(prefs.getBoolean("dark",false));box.addView(dark);
        Switch clean=new Switch(this);clean.setText("تنظيف الرموز غير المرغوبة");clean.setTextSize(16);clean.setGravity(Gravity.RIGHT);clean.setChecked(prefs.getBoolean("clean",true));box.addView(clean);
        Switch rtl=new Switch(this);rtl.setText("تنسيق Word من اليمين إلى اليسار");rtl.setTextSize(16);rtl.setGravity(Gravity.RIGHT);rtl.setChecked(prefs.getBoolean("rtl",true));box.addView(rtl);
        Switch headerFooter=new Switch(this);headerFooter.setText("استبعاد الترويسة والتذييل المتكررين");headerFooter.setTextSize(16);headerFooter.setGravity(Gravity.RIGHT);headerFooter.setChecked(prefs.getBoolean("headerFooter",true));box.addView(headerFooter);
        TextView loc=new TextView(this);loc.setText("مكان الحفظ: "+saveLocationLabel());loc.setTextSize(15);loc.setTextColor(greenDark());loc.setGravity(Gravity.RIGHT);loc.setPadding(5,14,5,8);loc.setOnClickListener(v->chooseSaveFolder());box.addView(loc);
        new AlertDialog.Builder(this).setView(box).setPositiveButton("حفظ", (d,w)->{prefs.edit().putBoolean("dark",dark.isChecked()).putBoolean("clean",clean.isChecked()).putBoolean("rtl",rtl.isChecked()).putBoolean("headerFooter",headerFooter.isChecked()).apply();applyTheme();recreate();}).setNegativeButton("إلغاء",null).show();
    }

    String saveLocationLabel(){return prefs.getString("saveTree","").isEmpty()?"ذاكرة الجهاز":"مجلد مخصص / ذاكرة خارجية";}
    void chooseSaveFolder(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,TREE);}
    void clearCache(){deleteRecursive(getCacheDir());toast("تم مسح التخزين المؤقت.");}
    void deleteRecursive(File f){if(f==null||!f.exists())return;if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File x:a)deleteRecursive(x);}if(!f.equals(getCacheDir()))f.delete();}
    void infoDialog(){new AlertDialog.Builder(this).setTitle("W-المخلافي").setMessage("محول المستندات إلى Word قابل للتحرير\nلا يوجد حد صفحات مصطنع في التطبيق.\nيمكن معالجة عدد كبير من الملفات بحسب مساحة وذاكرة الجهاز.").setPositiveButton("حسنًا",null).show();}

    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.setType("*/*");startActivityForResult(i,PICK);}
    void pickPdf(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.setType("application/pdf");startActivityForResult(i,PICK);}
    void pickImages(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.setType("image/*");startActivityForResult(i,PICK);}
    void camera(){
        try{File dir=new File(getExternalFilesDir("Camera"),"captures");if(!dir.exists())dir.mkdirs();File f=new File(dir,"capture_"+System.currentTimeMillis()+".jpg");prefs.edit().putString("cameraPath",f.getAbsolutePath()).apply();Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,u);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,CAMERA);}catch(Exception e){toast("تعذر فتح الكاميرا: "+e.getMessage());}
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==CREATE){if(c==RESULT_OK&&d!=null&&d.getData()!=null)savePendingTo(d.getData());else status.setText("تم حفظ النسخة داخل مستندات التطبيق.");return;}
        if(r==TREE){if(c==RESULT_OK&&d!=null&&d.getData()!=null){Uri u=d.getData();try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);}catch(Exception ignored){}prefs.edit().putString("saveTree",u.toString()).apply();toast("تم تعيين مكان الحفظ.");}return;}
        if(r==CAMERA){if(c==RESULT_OK){String path=prefs.getString("cameraPath","");if(!path.isEmpty()){File f=new File(path);if(f.exists())add(FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f));}}return;}
        if(c!=RESULT_OK||d==null||r!=PICK)return;
        if(d.getClipData()!=null)for(int i=0;i<d.getClipData().getItemCount();i++){Uri u=d.getClipData().getItemAt(i).getUri();persistRead(u);add(u);}
        else if(d.getData()!=null){persistRead(d.getData());add(d.getData());}
    }

    void persistRead(Uri u){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
    void add(Uri u){if(selected.contains(u))return;selected.add(u);if(selectedCard!=null)selectedCard.setVisibility(View.VISIBLE);if(status!=null)status.setVisibility(View.VISIBLE);TextView t=new TextView(this);t.setText("• "+name(u));t.setTextSize(15);t.setTextColor(text());t.setGravity(Gravity.RIGHT);t.setPadding(8,11,8,11);list.addView(t);}
    String name(Uri u){try{Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null){try{if(c.moveToFirst())return c.getString(0);}finally{c.close();}}}catch(Exception ignored){}return u.toString();}

    void createOutput(){
        if(selected.isEmpty()){toast("أضف ملفًا أولًا");return;}
        boolean hasPdf=false;
        for(Uri u:selected) if(name(u).toLowerCase(Locale.ROOT).endsWith(".pdf")){hasPdf=true;break;}
        if(hasPdf && !pageSelectionReady){showPdfPageSelection();return;}
        pageSelectionReady=false;
        progress.setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        status.setVisibility(View.VISIBLE);
        status.setText("بدء التحويل...");
        new Thread(()->{
            try{
                ArrayList<Uri> jobs=new ArrayList<>(selected);
                File tempText=new File(getCacheDir(),"ocr_stream_"+System.currentTimeMillis()+".txt");
                BufferedWriter out=new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tempText),"UTF-8"),65536);
                boolean singleDocx=jobs.size()==1 && name(jobs.get(0)).toLowerCase(Locale.ROOT).endsWith(".docx");
                if(singleDocx){
                    File direct=copyTemp(jobs.get(0),"docx");
                    pendingOutput=direct;
                    out.close(); tempText.delete();
                    runOnUiThread(()->{progress.setIndeterminate(false);progress.setProgressCompat(100,true);status.setText("تم تجهيز ملف Word مع الحفاظ على تنسيقه الأصلي.");saveResult();});
                    return;
                }
                int total=jobs.size(), index=0;
                for(Uri u:jobs){
                    index++;
                    final int idx=index;
                    runOnUiThread(()->status.setText("معالجة الملف "+idx+" من "+total+" : "+name(u)));
                    streamExtract(u,out);
                    if(index<total){out.write("\n");out.flush();}
                }
                out.close();
                File temp=new File(getCacheDir(),"Word_AlMakhlafi_"+System.currentTimeMillis()+".docx");
                writeDocxFromStream(temp,tempText);
                tempText.delete();
                pendingOutput=temp;
                runOnUiThread(()->{progress.setIndeterminate(false);progress.setProgressCompat(100,true);status.setText("اكتمل التحويل. جاري اختيار مكان الحفظ...");saveResult();});
            }catch(Exception e){
                runOnUiThread(()->{progress.setIndeterminate(false);progress.setVisibility(View.GONE);status.setText("تعذر التحويل: "+e.getMessage());toast("تعذر التحويل: "+e.getMessage());});
            }
        }).start();
    }

    void showPdfPageSelection(){
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20),dp(8),dp(20),dp(4));
        TextView h=sectionTitle("اختيار صفحات PDF"); box.addView(h);
        TextView info=new TextView(this);
        info.setText("اكتب رقم صفحة واحدة مثل 7، أو نطاقًا مثل 5-12، أو اختر كل الصفحات.");
        info.setTextSize(15);info.setTextColor(text());info.setGravity(Gravity.RIGHT);info.setPadding(4,4,4,12);box.addView(info);
        EditText range=new EditText(this);
        range.setHint("مثال: 5-12");range.setTextSize(18);range.setSingleLine(true);range.setGravity(Gravity.RIGHT);
        box.addView(range,new LinearLayout.LayoutParams(-1,dp(58)));
        CheckBox all=new CheckBox(this);
        all.setText("تحويل جميع صفحات PDF");all.setTextSize(16);all.setGravity(Gravity.RIGHT);all.setChecked(true);box.addView(all);
        all.setOnCheckedChangeListener((b,checked)->{range.setEnabled(!checked);if(checked)range.setText("");});
        new AlertDialog.Builder(this).setView(box)
            .setPositiveButton("بدء التحويل",(d,w)->{
                if(all.isChecked()){selectedPdfStartPage=1;selectedPdfEndPage=-1;pageSelectionReady=true;createOutput();return;}
                String s=range.getText().toString().trim().replace('–','-').replace('—','-').replace('−','-');
                try{
                    if(s.matches("\\d+")){int p=Integer.parseInt(s);if(p<1)throw new Exception();selectedPdfStartPage=p;selectedPdfEndPage=p;}
                    else if(s.matches("\\d+\\s*-\\s*\\d+")){String[] a=s.split("-");int from=Integer.parseInt(a[0].trim()),to=Integer.parseInt(a[1].trim());if(from<1||to<from)throw new Exception();selectedPdfStartPage=from;selectedPdfEndPage=to;}
                    else throw new Exception();
                    pageSelectionReady=true;createOutput();
                }catch(Exception ex){toast("أدخل رقم صفحة أو نطاقًا صحيحًا مثل 5-12.");}
            }).setNegativeButton("إلغاء",null).show();
    }

    void saveResult(){
        String tree=prefs.getString("saveTree","");
        if(!tree.isEmpty()){try{DocumentFile dir=DocumentFile.fromTreeUri(this,Uri.parse(tree));if(dir!=null&&dir.canWrite()){DocumentFile out=dir.createFile("application/vnd.openxmlformats-officedocument.wordprocessingml.document","Word_Al-Makhlafi_"+System.currentTimeMillis()+".docx");if(out!=null){OutputStream o=getContentResolver().openOutputStream(out.getUri());copyStream(new FileInputStream(pendingOutput),o);pendingOutput.delete();pendingOutput=null;progress.setVisibility(View.GONE);status.setText("تم حفظ الملف في المكان المحدد.");toast("تم الحفظ بنجاح.");return;}}}catch(Exception ignored){}}
        askSaveLocation();
    }
    void askSaveLocation(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");i.putExtra(Intent.EXTRA_TITLE,"Word_Al-Makhlafi_Converted.docx");startActivityForResult(i,CREATE);}
    void savePendingTo(Uri out){if(pendingOutput==null)return;try{OutputStream o=getContentResolver().openOutputStream(out);copyStream(new FileInputStream(pendingOutput),o);pendingOutput.delete();pendingOutput=null;progress.setVisibility(View.GONE);status.setText("تم حفظ الملف بنجاح.");toast("تم الحفظ بنجاح.");}catch(Exception e){toast("تعذر الحفظ: "+e.getMessage());}}

    String extract(Uri u)throws Exception{
        String n=name(u).toLowerCase(Locale.ROOT);
        if(n.endsWith(".pdf"))return ocrPdf(u);
        if(n.matches(".*\\.(jpg|jpeg|png|bmp|tif|tiff|webp)$"))return ocrBitmap(decodeScaled(u));
        if(n.endsWith(".xlsx"))return extractXlsxAsTable(u);
        if(n.endsWith(".pptx"))return extractPptx(u);
        if(n.endsWith(".docx"))return extractDocxText(u);
        return "الملف بصيغة غير مدعومة.";
    }

    String ocrPdf(Uri u)throws Exception{
        File f=copyTemp(u,"pdf");
        PdfRenderer r=new PdfRenderer(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY));
        int count=r.getPageCount();
        int from=Math.max(1,Math.min(selectedPdfStartPage,count));
        int to=selectedPdfEndPage<1?count:Math.min(selectedPdfEndPage,count);
        if(from>to){r.close();f.delete();throw new Exception("نطاق الصفحات خارج عدد صفحات ملف PDF.");}
        StringBuilder result=new StringBuilder();
        for(int i=from-1;i<to;i++){
            final int page=i+1;
            runOnUiThread(()->status.setText("استخراج الصفحة "+page+" من "+to));
            PdfRenderer.Page p=r.openPage(i);
            double pageW=p.getWidth(), pageH=p.getHeight();
            float scale=Math.min(2.8f,Math.max(1.45f,2200f/Math.max(p.getWidth(),p.getHeight())));
            int w=Math.max(1200,(int)(p.getWidth()*scale));
            int h=Math.max(1200,(int)(p.getHeight()*scale));
            Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);
            p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            p.close();
            String pageText=ocrBitmapLayout(b,pageW,pageH);
            b.recycle();
            if(pageText!=null&&!pageText.trim().isEmpty()){
                result.append("<<PDFPAGE ").append(pageW).append(" ").append(pageH).append(" ").append(w).append(" ").append(h).append(">>\n");
                result.append(pageText).append("<<ENDPDFPAGE>>\n");
            }
        }
        r.close();f.delete();
        return result.toString();
    }

    String cleanPages(ArrayList<String> pages){
        ArrayList<String> cleaned=new ArrayList<>();
        for(String s:pages) cleaned.add(normalizeText(cleanOcr(s==null?"":s)));
        return joinPages(removeRepeatedHeadersFooters(cleaned));
    }

    String joinPages(ArrayList<String> pages){
        StringBuilder b=new StringBuilder();
        for(String s:pages){
            String q=normalizeText(s);
            if(q.isEmpty()) continue;
            if(b.length()>0)b.append("\\n");
            b.append(q);
        }
        return b.toString();
    }
    String key(String s){return s.replaceAll("[\\s\\p{Punct}]+","").trim();}
    boolean isPageNumber(String s){return s.trim().matches("[0-9٠-٩]{1,5}");}

    Bitmap decodeScaled(Uri u)throws Exception{InputStream in=getContentResolver().openInputStream(u);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeStream(in,null,o);in.close();int max=2800,sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>max)sample*=2;in=getContentResolver().openInputStream(u);o.inJustDecodeBounds=false;o.inSampleSize=sample;Bitmap b=BitmapFactory.decodeStream(in,null,o);in.close();return b;}

    String ocrBitmap(Bitmap b)throws Exception{
        if(b==null)throw new Exception("تعذر قراءة الصورة");
        File td=new File(getFilesDir(),"tessdata");if(!td.exists())td.mkdirs();
        asset("tessdata/ara.traineddata",new File(td,"ara.traineddata"));
        TessBaseAPI t=new TessBaseAPI();
        if(!t.init(getFilesDir().getAbsolutePath(),"ara"))throw new Exception("فشل تشغيل OCR العربي");
        t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
        t.setVariable("preserve_interword_spaces","1");
        t.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST,"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz|¦~\\u0060^\\\\");
        Bitmap prepared=prepareForOcr(b);
        t.setImage(prepared);
        String x=t.getUTF8Text();
        t.recycle();
        if(prepared!=b)prepared.recycle();
        return normalizeText(cleanOcr(x==null?"":x));
    }

    String ocrBitmapLayout(Bitmap b,double pageW,double pageH)throws Exception{
        if(b==null)throw new Exception("تعذر قراءة الصفحة");
        File td=new File(getFilesDir(),"tessdata");if(!td.exists())td.mkdirs();
        asset("tessdata/ara.traineddata",new File(td,"ara.traineddata"));
        TessBaseAPI t=new TessBaseAPI();
        if(!t.init(getFilesDir().getAbsolutePath(),"ara"))throw new Exception("فشل تشغيل OCR العربي");
        t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);
        t.setVariable("preserve_interword_spaces","1");
        t.setVariable("user_defined_dpi","300");
        t.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST,"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz|¦~\\`^");
        Bitmap prepared=prepareForOcr(b);
        t.setImage(prepared);
        t.getUTF8Text();
        ArrayList<LayoutLine> lines=new ArrayList<>();
        ResultIterator it=t.getResultIterator();
        if(it!=null){
            it.begin();
            do{
                if(it.isAtBeginningOf(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE)){
                    String tx=it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE);
                    Rect rc=it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE);
                    float conf=it.confidence(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE);
                    tx=normalizeText(cleanOcr(tx==null?"":tx));
                    if(!tx.isEmpty()&&rc!=null&&rc.width()>3&&rc.height()>3&&conf>=5){
                        double cy=rc.centerY()/(double)Math.max(1,b.getHeight());
                        if(isPageNumber(tx)||cy<0.045||cy>0.955)continue;
                        lines.add(new LayoutLine(tx,rc.left,rc.top,rc.width(),rc.height(),conf));
                    }
                }
            }while(it.next(TessBaseAPI.PageIteratorLevel.RIL_TEXTLINE));
            it.delete();
        }
        t.recycle();
        if(prepared!=b)prepared.recycle();
        Collections.sort(lines,(x,y)->{
            int dy=Math.abs(x.y-y.y);
            if(dy<=Math.max(12,Math.min(x.h,y.h)/2))return Integer.compare(y.x,x.x);
            return Integer.compare(x.y,y.y);
        });
        StringBuilder out=new StringBuilder();
        for(LayoutLine l:lines){
            out.append("<<L ").append(l.x).append(" ").append(l.y).append(" ").append(l.w).append(" ").append(l.h).append(">>");
            out.append(l.text.replace("\n"," ").replace("\r"," ")).append("\n");
        }
        return out.toString();
    }

    static class LayoutLine{
        String text; int x,y,w,h; float confidence;
        LayoutLine(String t,int x,int y,int w,int h,float c){text=t;this.x=x;this.y=y;this.w=w;this.h=h;confidence=c;}
    }

    int currentRenderedWidth=1;
    int currentRenderedHeight=1;

    Bitmap prepareForOcr(Bitmap src){
        if(src==null)return null;
        Bitmap out=Bitmap.createBitmap(src.getWidth(),src.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(out);
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        ColorMatrix m=new ColorMatrix();m.setSaturation(0f);
        paint.setColorFilter(new ColorMatrixColorFilter(m));
        canvas.drawColor(Color.WHITE);
        canvas.drawBitmap(src,0,0,paint);
        return out;
    }

    String cleanOcr(String x){
        if(x==null)return "";
        StringBuilder out=new StringBuilder();
        for(int i=0;i<x.length();i++){
            char c=x.charAt(i);
            boolean arabic=(c>='\u0600'&&c<='\u06FF')||(c>='\u0750'&&c<='\u077F')||(c>='\u08A0'&&c<='\u08FF');
            boolean digit=(c>='0'&&c<='9')||(c>='\u0660'&&c<='\u0669')||(c>='\u06F0'&&c<='\u06F9');
            boolean punct=" ،؛:،.؟!?-_/()[]{}%+*=\"'".indexOf(c)>=0;
            if(Character.isWhitespace(c)||arabic||digit||punct)out.append(c); else out.append(' ');
        }
        String q=out.toString().replaceAll("[A-Za-z]+"," ");
        q=q.replaceAll("[|¦~\\u0060^\\\\]+"," ");
        q=q.replaceAll("([،؛:؟.!-])\\1{2,}","$1");
        q=q.replaceAll("(?m)^\\s*[|¦~\\u0060^]+\\s*$","");
        q=q.replaceAll("[ ]{2,}"," ");
        return q;
    }

    String normalizeText(String x){
        if(x==null)return "";
        String s=x.replace("\u0640","");
        s=s.replaceAll("(?m)[ \\t]+"," ");
        s=s.replaceAll(" *\\n *","\\n");
        s=s.replaceAll(" +([،؛:؟,.!])","$1");
        s=s.replaceAll("([،؛:؟,.!])\\1+","$1");
        s=normalizeDates(s);
        String[] bad={"هاذا","هاذه","هاؤلاء","اللذي","الذيي","التيي","لاكن","ولكنن","مسوول","مسئول","شيى","شئ","جزءا"};
        String[] good={"هذا","هذه","هؤلاء","الذي","الذي","التي","لكن","ولكن","مسؤول","مسؤول","شيء","شيء","جزءاً"};
        for(int i=0;i<bad.length;i++)s=s.replaceAll("(?<![\\u0600-\\u06FF])"+bad[i]+"(?![\\u0600-\\u06FF])",good[i]);
        if(prefs.getBoolean("clean",true)){s=s.replaceAll("[|¦~\\`^]+"," ");s=s.replaceAll("[ ]{2,}"," ");}
        return s.trim();
    }

    String normalizeDates(String s){
        if(s==null||s.isEmpty())return "";
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("([0-9٠-٩]{1,4})\\s*[-./]\\s*([0-9٠-٩]{1,2})\\s*[-./]\\s*([0-9٠-٩]{1,4})").matcher(s);
        StringBuffer out=new StringBuffer();
        while(m.find()){
            String a=digitsToAscii(m.group(1)),b=digitsToAscii(m.group(2)),d=digitsToAscii(m.group(3)),rep=m.group();
            if(a.length()==4&&d.length()<=2)rep=d+"/"+b+"/"+a;
            else if(d.length()==4)rep=a+"/"+b+"/"+d;
            m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(rep));
        }
        m.appendTail(out);
        return out.toString();
    }

    String digitsToAscii(String s){
        if(s==null)return "";
        StringBuilder b=new StringBuilder();
        for(char c:s.toCharArray()){
            if(c>='٠'&&c<='٩')b.append((char)('0'+c-'٠'));
            else if(c>='۰'&&c<='۹')b.append((char)('0'+c-'۰'));
            else b.append(c);
        }
        return b.toString();
    }

    void asset(String a,File d)throws Exception{if(d.exists()&&d.length()>1000)return;InputStream in=getAssets().open(a);FileOutputStream o=new FileOutputStream(d);byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);in.close();o.close();}
    String extractZipXml(Uri u)throws Exception{
        String n=name(u).toLowerCase(Locale.ROOT);
        if(n.endsWith(".xlsx"))return extractXlsxAsTable(u);
        if(n.endsWith(".pptx"))return extractPptx(u);
        return extractDocxText(u);
    }

    File copyTemp(Uri u,String ext)throws Exception{File f=new File(getCacheDir(),System.currentTimeMillis()+"."+ext);InputStream in=getContentResolver().openInputStream(u);FileOutputStream o=new FileOutputStream(f);copyStream(in,o);return f;}
    void writeDocx(File out,String text)throws Exception{
        File tmp=new File(getCacheDir(),"docx_text_"+System.currentTimeMillis()+".txt");
        Writer w=new OutputStreamWriter(new FileOutputStream(tmp),"UTF-8");w.write(text);w.close();
        writeDocxFromStream(out,tmp);tmp.delete();
    }

    void streamExtract(Uri u,BufferedWriter out)throws Exception{
        String text=extract(u);
        if(text==null)return;
        out.write(text);
        out.write("\n");
    }

    String extractDocxText(Uri u)throws Exception{
        File f=copyTemp(u,"docx");
        ZipFile z=new ZipFile(f);
        StringBuilder s=new StringBuilder();
        Enumeration<? extends ZipEntry> es=z.entries();
        while(es.hasMoreElements()){
            ZipEntry e=es.nextElement();
            if(!e.getName().equals("word/document.xml"))continue;
            InputStream in=z.getInputStream(e);
            Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList ps=d.getElementsByTagNameNS("*","p");
            for(int i=0;i<ps.getLength();i++){
                NodeList ts=((Element)ps.item(i)).getElementsByTagNameNS("*","t");
                for(int j=0;j<ts.getLength();j++)s.append(ts.item(j).getTextContent());
                s.append("\n");
            }
            in.close();
        }
        z.close();f.delete();
        return normalizeText(s.toString());
    }

    String extractPptx(Uri u)throws Exception{
        File f=copyTemp(u,"pptx");
        ZipFile z=new ZipFile(f);
        ArrayList<String> names=new ArrayList<>();
        Enumeration<? extends ZipEntry> es=z.entries();
        while(es.hasMoreElements()){
            String n=es.nextElement().getName();
            if(n.matches("ppt/slides/slide[0-9]+\\.xml"))names.add(n);
        }
        Collections.sort(names,(a,b)->{
            int ia=Integer.parseInt(a.replaceAll("\\D",""));
            int ib=Integer.parseInt(b.replaceAll("\\D",""));
            return Integer.compare(ia,ib);
        });
        StringBuilder s=new StringBuilder();
        int no=0;
        for(String en:names){
            no++;
            ZipEntry e=z.getEntry(en);
            InputStream in=z.getInputStream(e);
            Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList ts=d.getElementsByTagNameNS("*","t");
            if(ts.getLength()>0){
                s.append("الشريحة ").append(no).append("\n");
                for(int i=0;i<ts.getLength();i++){
                    String q=ts.item(i).getTextContent().trim();
                    if(!q.isEmpty())s.append(q).append(" ");
                }
                s.append("\n\n");
            }
            in.close();
        }
        z.close();f.delete();
        return normalizeText(s.toString());
    }

    String extractXlsxAsTable(Uri u)throws Exception{
        File f=copyTemp(u,"xlsx");
        ZipFile z=new ZipFile(f);
        ArrayList<String> shared=new ArrayList<>();
        ZipEntry ss=z.getEntry("xl/sharedStrings.xml");
        if(ss!=null){
            InputStream in=z.getInputStream(ss);
            Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList si=d.getElementsByTagNameNS("*","si");
            for(int i=0;i<si.getLength();i++){
                NodeList ts=((Element)si.item(i)).getElementsByTagNameNS("*","t");
                StringBuilder q=new StringBuilder();
                for(int j=0;j<ts.getLength();j++)q.append(ts.item(j).getTextContent());
                shared.add(q.toString());
            }
            in.close();
        }
        ArrayList<String> sheets=new ArrayList<>();
        Enumeration<? extends ZipEntry> es=z.entries();
        while(es.hasMoreElements()){
            String n=es.nextElement().getName();
            if(n.matches("xl/worksheets/sheet[0-9]+\\.xml"))sheets.add(n);
        }
        Collections.sort(sheets,(a,b)->Integer.compare(Integer.parseInt(a.replaceAll("\\D","")),Integer.parseInt(b.replaceAll("\\D",""))));
        StringBuilder result=new StringBuilder();
        for(String sn:sheets){
            InputStream in=z.getInputStream(z.getEntry(sn));
            Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
            NodeList rows=d.getElementsByTagNameNS("*","row");
            result.append("<<TABLE>>\n");
            for(int r=0;r<rows.getLength();r++){
                Element row=(Element)rows.item(r);
                NodeList cells=row.getElementsByTagNameNS("*","c");
                ArrayList<String> vals=new ArrayList<>();
                for(int c=0;c<cells.getLength();c++){
                    Element cell=(Element)cells.item(c);
                    String type=cell.getAttribute("t");
                    NodeList vs=cell.getElementsByTagNameNS("*","v");
                    String val=vs.getLength()>0?vs.item(0).getTextContent():"";
                    if("s".equals(type)){
                        try{int k=Integer.parseInt(val);if(k>=0&&k<shared.size())val=shared.get(k);}catch(Exception ignored){}
                    }else if("inlineStr".equals(type)){
                        NodeList ts=cell.getElementsByTagNameNS("*","t");
                        StringBuilder q=new StringBuilder();for(int j=0;j<ts.getLength();j++)q.append(ts.item(j).getTextContent());val=q.toString();
                    }
                    vals.add(normalizeText(val));
                }
                if(!vals.isEmpty())result.append(String.join("\t",vals)).append("\n");
            }
            result.append("<</TABLE>>\n");
            in.close();
        }
        z.close();f.delete();
        return result.toString();
    }

    ArrayList<String> pageLines(String s){
        ArrayList<String> a=new ArrayList<>();
        if(s==null)return a;
        for(String x:s.split("\\R",-1)){x=x.trim();if(!x.isEmpty())a.add(x);}
        return a;
    }

    ArrayList<String> removeRepeatedHeadersFooters(ArrayList<String> pages){
        int n=pages.size();
        if(n<2||!prefs.getBoolean("headerFooter",true))return pages;
        HashMap<String,Integer> top=new HashMap<>(),bottom=new HashMap<>();
        for(String p:pages){
            ArrayList<String> ls=pageLines(p);
            if(!ls.isEmpty())top.put(key(ls.get(0)),top.getOrDefault(key(ls.get(0)),0)+1);
            if(ls.size()>1)bottom.put(key(ls.get(ls.size()-1)),bottom.getOrDefault(key(ls.get(ls.size()-1)),0)+1);
        }
        int threshold=Math.max(2,(int)Math.ceil(n*0.60));
        HashSet<String> rt=new HashSet<>(),rb=new HashSet<>();
        for(Map.Entry<String,Integer> e:top.entrySet())if(e.getValue()>=threshold&&!e.getKey().isEmpty())rt.add(e.getKey());
        for(Map.Entry<String,Integer> e:bottom.entrySet())if(e.getValue()>=threshold&&!e.getKey().isEmpty())rb.add(e.getKey());
        ArrayList<String> out=new ArrayList<>();
        for(String p:pages){
            ArrayList<String> ls=pageLines(p);
            StringBuilder b=new StringBuilder();
            for(int i=0;i<ls.size();i++){
                String q=ls.get(i),k=key(q);
                boolean skip=(i==0&&rt.contains(k))||(i==ls.size()-1&&rb.contains(k))||(isPageNumber(q)&&(i<2||i>=ls.size()-2));
                if(!skip){if(b.length()>0)b.append("\n");b.append(q);}
            }
            out.add(b.toString());
        }
        return out;
    }

    String cleanPageMarkers(String text){
        if(text==null)return "";
        String[] pages=text.split("<<PAGE [0-9]+>>");
        ArrayList<String> p=new ArrayList<>();
        for(String q:pages){q=normalizeText(cleanOcr(q));if(!q.trim().isEmpty())p.add(q);}
        return joinPages(removeRepeatedHeadersFooters(p));
    }

    void writeDocxFromStream(File out,File source)throws Exception{
        ZipOutputStream z=new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(out)));
        put(z,"[Content_Types].xml","<?xml version='1.0'?><Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'><Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/><Default Extension='xml' ContentType='application/xml'/><Override PartName='/word/document.xml' ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/></Types>");
        put(z,"_rels/.rels","<?xml version='1.0'?><Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'><Relationship Id='rId1' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument' Target='word/document.xml'/></Relationships>");
        put(z,"word/_rels/document.xml.rels","<?xml version='1.0'?><Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'/>");
        BufferedReader r=new BufferedReader(new InputStreamReader(new FileInputStream(source),"UTF-8"),65536);
        StringBuilder body=new StringBuilder("<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'><w:body>");
        String line;boolean table=false,pdfPage=false;double pageW=595,pageH=842;int renderW=1,renderH=1;
        ArrayList<LayoutLine> pageLines=new ArrayList<>();
        while((line=r.readLine())!=null){
            if(line.startsWith("<<PDFPAGE ")){
                pageLines.clear();
                String[] a=line.substring(10,line.length()-2).trim().split(" ");
                if(a.length>=4){pageW=Double.parseDouble(a[0]);pageH=Double.parseDouble(a[1]);renderW=Integer.parseInt(a[2]);renderH=Integer.parseInt(a[3]);}
                pdfPage=true;continue;
            }
            if(pdfPage&&line.startsWith("<<L ")&&line.contains(">>")){
                int k=line.indexOf(">>");String[] a=line.substring(4,k).trim().split(" ");
                if(a.length>=4)try{
                    int x=Integer.parseInt(a[0]),y=Integer.parseInt(a[1]),ww=Integer.parseInt(a[2]),hh=Integer.parseInt(a[3]);
                    String tx=normalizeText(cleanOcr(line.substring(k+2)));
                    if(!tx.isEmpty())pageLines.add(new LayoutLine(tx,x,y,ww,hh,100));
                }catch(Exception ignored){}
                continue;
            }
            if(pdfPage&&line.startsWith("<<ENDPDFPAGE>>")){
                writePdfPage(body,pageLines,pageW,pageH,renderW,renderH);pdfPage=false;continue;
            }
            if(pdfPage)continue;
            if(line.equals("<<TABLE>>")){table=true;body.append("<w:tbl><w:tblPr><w:tblBorders><w:top w:val='single'/><w:left w:val='single'/><w:bottom w:val='single'/><w:right w:val='single'/><w:insideH w:val='single'/><w:insideV w:val='single'/></w:tblBorders></w:tblPr>");continue;}
            if(line.equals("<</TABLE>>")){table=false;body.append("</w:tbl>");continue;}
            if(table){
                body.append("<w:tr>");
                for(String cell:line.split("\\t",-1))
                    body.append("<w:tc><w:p><w:pPr><w:jc w:val='both'/><w:bidi/><w:spacing w:before='0' w:after='0' w:line='240' w:lineRule='auto'/></w:pPr><w:r><w:rPr><w:rtl/><w:rFonts w:ascii='DecoType Naskh' w:hAnsi='DecoType Naskh' w:eastAsia='DecoType Naskh' w:cs='DecoType Naskh'/></w:rPr><w:t xml:space='preserve'>").append(xml(normalizeDates(cell))).append("</w:t></w:r></w:p></w:tc>");
                body.append("</w:tr>");
            }else if(!line.trim().isEmpty()){
                body.append("<w:p><w:pPr><w:jc w:val='both'/><w:bidi/><w:spacing w:before='0' w:after='0' w:line='276' w:lineRule='auto'/></w:pPr><w:r><w:rPr><w:rtl/><w:rFonts w:ascii='DecoType Naskh' w:hAnsi='DecoType Naskh' w:eastAsia='DecoType Naskh' w:cs='DecoType Naskh'/></w:rPr><w:t xml:space='preserve'>").append(xml(normalizeDates(line))).append("</w:t></w:r></w:p>");
            }
        }
        r.close();
        body.append("<w:sectPr><w:pgSz w:w='11906' w:h='16838'/><w:pgMar w:top='720' w:right='720' w:bottom='720' w:left='720'/></w:sectPr></w:body></w:document>");
        put(z,"word/document.xml",body.toString());z.close();
    }

    void writePdfPage(StringBuilder body,ArrayList<LayoutLine> lines,double pageW,double pageH,int renderW,int renderH){
        if(lines==null||lines.isEmpty())return;
        Collections.sort(lines,(a,b)->{
            int dy=Math.abs(a.y-b.y);
            if(dy<=Math.max(12,Math.min(a.h,b.h)/2))return Integer.compare(b.x,a.x);
            return Integer.compare(a.y,b.y);
        });
        double sx=pageW/Math.max(1,renderW),sy=pageH/Math.max(1,renderH);
        int minX=renderW,maxX=0,minY=renderH,maxY=0;
        for(LayoutLine l:lines){
            minX=Math.min(minX,l.x);maxX=Math.max(maxX,l.x+l.w);
            minY=Math.min(minY,l.y);maxY=Math.max(maxY,l.y+l.h);
        }
        double leftMargin=Math.max(18,Math.min(54,minX*sx-4));
        double rightMargin=Math.max(18,Math.min(54,(renderW-maxX)*sx-4));
        double topMargin=Math.max(14,Math.min(50,minY*sy-3));
        double bottomMargin=Math.max(14,Math.min(50,(renderH-maxY)*sy-3));

        ArrayList<ArrayList<LayoutLine>> paras=new ArrayList<>();
        ArrayList<LayoutLine> cur=new ArrayList<>();LayoutLine prev=null;
        for(LayoutLine l:lines){
            if(prev==null)cur.add(l);
            else{
                double gap=(l.y-(prev.y+prev.h))*sy;
                double threshold=Math.max(7,Math.min(18,Math.max(prev.h,l.h)*sy*0.78));
                if(gap>threshold){
                    if(!cur.isEmpty())paras.add(cur);
                    cur=new ArrayList<>();
                }
                cur.add(l);
            }
            prev=l;
        }
        if(!cur.isEmpty())paras.add(cur);

        for(ArrayList<LayoutLine> para:paras){
            int maxH=1;for(LayoutLine l:para)maxH=Math.max(maxH,l.h);
            int font=(int)Math.max(10,Math.min(18,(maxH*sy)*0.56));
            boolean heading=isPdfHeading(para);
            if(heading)font=Math.min(19,font+1);

            StringBuilder paragraphText=new StringBuilder();
            for(LayoutLine l:para){
                String tx=normalizeDates(l.text);
                if(tx.isEmpty())continue;
                if(paragraphText.length()>0)paragraphText.append(' ');
                paragraphText.append(tx);
            }
            String tx=fixArabicSpacing(paragraphText.toString().trim());
            if(tx.isEmpty())continue;

            body.append("<w:p><w:pPr><w:jc w:val='both'/><w:bidi/>")
                .append("<w:spacing w:before='0' w:after='0' w:line='")
                .append(Math.max(220,(int)(maxH*sy*20*0.76)))
                .append("' w:lineRule='auto'/><w:widowControl w:val='0'/></w:pPr>");
            body.append("<w:r><w:rPr><w:rtl/><w:bidi/><w:rFonts w:ascii='DecoType Naskh' w:hAnsi='DecoType Naskh' w:eastAsia='DecoType Naskh' w:cs='DecoType Naskh'/>")
                .append("<w:sz w:val='").append(font*2).append("'/><w:szCs w:val='").append(font*2).append("'/>");
            if(heading)body.append("<w:b/><w:bCs/>");
            body.append("</w:rPr><w:t xml:space='preserve'>").append(xml(tx)).append("</w:t></w:r></w:p>");
        }

        int w=(int)(pageW*20),h=(int)(pageH*20);
        body.append("<w:p><w:pPr><w:pageBreakBefore/><w:sectPr><w:type w:val='nextPage'/><w:bidi/><w:pgSz w:w='")
            .append(w).append("' w:h='").append(h)
            .append("'/><w:pgMar w:top='").append((int)(topMargin*20))
            .append("' w:right='").append((int)(rightMargin*20))
            .append("' w:bottom='").append((int)(bottomMargin*20))
            .append("' w:left='").append((int)(leftMargin*20))
            .append("'/></w:sectPr></w:pPr></w:p>");
    }

    String fixArabicSpacing(String s){
        if(s==null||s.isEmpty())return "";
        // OCR sometimes drops the space after short Arabic prepositions.
        s=s.replaceAll("([\\u0600-\\u06FF])(من|في|إلى|الى)(?=(ال|[\\u0621-\\u064A]))","$1 $2 ");
        s=s.replaceAll("(^|[\\s،؛:])و(من|في|إلى|الى)(?=(ال|[\\u0621-\\u064A]))","$1و$2 ");
        s=s.replaceAll("\\s{2,}"," ");
        return s.trim();
    }

    boolean isPdfHeading(ArrayList<LayoutLine> para){
        if(para==null||para.isEmpty())return false;
        String s=para.get(0).text.trim();
        if(s.length()>120)return false;
        if(s.matches("^(أولاً|ثانياً|ثالثاً|رابعاً|أولاً:|ثانياً:|ثالثاً:|رابعاً:).*"))return true;
        if(s.matches("^[٠-٩0-9]+[.)].*"))return true;
        if(s.endsWith(":")||s.endsWith("؛"))return true;
        return para.size()==1&&s.length()<80;
    }



    void put(ZipOutputStream z,String n,String s)throws Exception{z.putNextEntry(new ZipEntry(n));z.write(s.getBytes("UTF-8"));z.closeEntry();}
    String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    void copyStream(InputStream in,OutputStream out)throws Exception{try{byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}finally{try{in.close();}catch(Exception ignored){}try{out.close();}catch(Exception ignored){}}}

    void showLibrary(){
        File dir=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"WordAlMakhlafi");if(!dir.exists())dir.mkdirs();File[] fs=dir.listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".docx"));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(20,10,20,10);TextView h=sectionTitle("حافظة المستندات");box.addView(h);
        if(fs==null||fs.length==0){TextView e=new TextView(this);e.setText("لا توجد مستندات محفوظة بعد.");e.setGravity(Gravity.RIGHT);e.setPadding(5,20,5,20);box.addView(e);}
        else for(File f:fs){MaterialButton b=menuButton("📄 "+f.getName());b.setOnClickListener(v->openDoc(f));box.addView(b);}
        new AlertDialog.Builder(this).setView(box).setPositiveButton("إغلاق",null).show();
    }
    void openDoc(File f){try{Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,"application/vnd.openxmlformats-officedocument.wordprocessingml.document");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){toast("لا يوجد تطبيق لفتح Word.");}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}