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

    View ui(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(247,252,249));
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(14,10,14,16);
        page.setBackgroundColor(Color.rgb(247,252,249));

        // ===== Compact header: title only, no crowding =====
        MaterialCardView hero=card();
        hero.setCardBackgroundColor(Color.rgb(7,126,82));
        hero.setStrokeWidth(0);
        LinearLayout h=new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(12,8,12,8);

        TextView title=new TextView(this);
        title.setText("W-المخلافي");
        title.setTextColor(Color.WHITE);
        title.setTextSize(29);
        title.setTypeface(null,Typeface.BOLD);
        title.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        h.addView(title,new LinearLayout.LayoutParams(0,64,1));

        TextView logo=new TextView(this);
        logo.setText("W");
        logo.setTextColor(greenDark());
        logo.setTextSize(30);
        logo.setTypeface(null,Typeface.BOLD);
        logo.setGravity(Gravity.CENTER);
        logo.setBackground(gradient(Color.WHITE,Color.rgb(225,255,243),20));
        h.addView(logo,new LinearLayout.LayoutParams(62,62));

        MaterialButton headerSettings=smallHeaderButton("⚙");
        headerSettings.setOnClickListener(v->settingsDialog());
        h.addView(headerSettings,new LinearLayout.LayoutParams(54,58));

        hero.addView(h);
        page.addView(hero,new LinearLayout.LayoutParams(-1,82));

        // ===== Dhikr =====
        MaterialCardView dhikr=card();
        dhikr.setCardBackgroundColor(Color.rgb(226,248,239));
        dhikr.setStrokeColor(Color.rgb(190,225,211));
        dhikr.setStrokeWidth(1);
        TextView d=new TextView(this);
        d.setText("صلِّ على محمد صلى الله عليه وسلم\\nوعلى آله الطيبين الطاهرين");
        d.setTextColor(greenDark());
        d.setTextSize(16);
        d.setTypeface(null,Typeface.BOLD);
        d.setGravity(Gravity.CENTER);
        d.setPadding(10,5,10,5);
        dhikr.addView(d);
        page.addView(dhikr,new LinearLayout.LayoutParams(-1,72));

        // ===== Main actions =====
        LinearLayout actions=new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0,7,0,5);

        MaterialCardView cameraCard=featureCard("📷","المسح الضوئي\\nبالكاميرا","التقاط صورة وتحويلها إلى وورد");
        cameraCard.setOnClickListener(v->camera());
        MaterialCardView importCard=featureCard("📂","استيراد الملفات","من الجهاز أو الذاكرة الخارجية");
        importCard.setOnClickListener(v->pick());
        actions.addView(cameraCard,new LinearLayout.LayoutParams(0,112,1));
        actions.addView(importCard,new LinearLayout.LayoutParams(0,112,1));
        page.addView(actions);

        // ===== Conversion menu: commands hidden until pressed =====
        MaterialButton convert=greenOutlineButton("⇄   تحويلات   ﹀");
        page.addView(convert,new LinearLayout.LayoutParams(-1,56));

        final LinearLayout convertPanel=referencePanel();
        convertPanel.setVisibility(View.GONE);
        addReferenceItem(convertPanel,"📄","PDF إلى Word",v->pickPdf());
        addReferenceItem(convertPanel,"🖨","PDF ممسوح → OCR → Word",v->pickPdf());
        addReferenceItem(convertPanel,"🖼","الصور إلى Word",v->pickImages());
        addReferenceItem(convertPanel,"𝕏","Excel إلى Word",v->pick());
        addReferenceItem(convertPanel,"W","Word إلى Word",v->pick());
        addReferenceItem(convertPanel,"P","PowerPoint إلى Word",v->pick());
        addReferenceItem(convertPanel,"📚","تحويل مجموعة ملفات دفعة واحدة",v->pick());
        page.addView(convertPanel);

        convert.setOnClickListener(v->{
            convertPanel.setVisibility(convertPanel.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);
        });

        // ===== Quick tools =====
        page.addView(sectionHeader("⚡  أدوات سريعة"),new LinearLayout.LayoutParams(-1,46));
        LinearLayout quick=new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        addQuick(quick,"📷","المسح الضوئي","بالكاميرا",v->camera());
        addQuick(quick,"📄","PDF إلى Word","سريع ودقيق",v->pickPdf());
        addQuick(quick,"🖼","صور إلى Word","بجودة عالية",v->pickImages());
        addQuick(quick,"📚","ملفات متعددة","دفعة واحدة",v->pick());
        page.addView(quick,new LinearLayout.LayoutParams(-1,104));

        // ===== Recent files =====
        LinearLayout rh=new LinearLayout(this);
        rh.setGravity(Gravity.CENTER_VERTICAL);
        TextView recent=sectionHeader("◷  الملفات الحديثة");
        TextView all=new TextView(this);
        all.setText("عرض الكل");
        all.setTextSize(13);
        all.setTextColor(Color.DKGRAY);
        all.setGravity(Gravity.CENTER);
        all.setBackground(gradient(Color.WHITE,Color.rgb(238,248,243),22));
        rh.addView(recent,new LinearLayout.LayoutParams(0,46,1));
        rh.addView(all,new LinearLayout.LayoutParams(92,40));
        page.addView(rh);

        LinearLayout recentCards=new LinearLayout(this);
        recentCards.setOrientation(LinearLayout.HORIZONTAL);
        addRecentPlaceholder(recentCards,"مستند 1");
        addRecentPlaceholder(recentCards,"صورة 2");
        addRecentPlaceholder(recentCards,"PDF ملف");
        addRecentPlaceholder(recentCards,"مستند 4");
        page.addView(recentCards,new LinearLayout.LayoutParams(-1,116));

        // ===== Selected files: hidden until user selects files =====
        selectedCard=card();
        selectedCard.setStrokeColor(Color.rgb(50,180,130));
        selectedCard.setStrokeWidth(1);
        LinearLayout filesBox=new LinearLayout(this);
        filesBox.setOrientation(LinearLayout.VERTICAL);
        filesBox.addView(sectionHeader("📂  الملفات المحددة"));
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView fileScroll=new ScrollView(this);
        fileScroll.addView(list);
        filesBox.addView(fileScroll,new LinearLayout.LayoutParams(-1,100));
        selectedCard.addView(filesBox);
        selectedCard.setVisibility(View.GONE);
        page.addView(selectedCard,new LinearLayout.LayoutParams(-1,122));

        status=new TextView(this);
        status.setText("جاهز");
        status.setTextColor(greenDark());
        status.setTextSize(14);
        status.setTypeface(null,Typeface.BOLD);
        status.setGravity(Gravity.CENTER);
        status.setBackground(gradient(Color.rgb(231,249,241),Color.WHITE,16));
        status.setVisibility(View.GONE);
        page.addView(status,new LinearLayout.LayoutParams(-1,46));

        progress=new LinearProgressIndicator(this);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        page.addView(progress,new LinearLayout.LayoutParams(-1,4));

        MaterialButton start=greenMainButton("▶   بدء التحويل");
        start.setOnClickListener(v->createOutput());
        page.addView(start,new LinearLayout.LayoutParams(-1,54));

        scroll.addView(page,new ScrollView.LayoutParams(-1,-2));
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        // ===== Footer navigation: secondary menus live here =====
        LinearLayout nav=new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(4,3,4,3);
        nav.setBackgroundColor(Color.WHITE);

        addNav(nav,"⋯","المزيد",v->showMorePopup(v));
        addNav(nav,"⚙","الإعدادات",v->settingsDialog());
        addNav(nav,"◷","السجل",v->toast("سجل التحويلات"));
        addNav(nav,"□","المكتبة",v->showLibrary());
        addNav(nav,"⌂","الرئيسية",v->scroll.smoothScrollTo(0,0));
        root.addView(nav,new LinearLayout.LayoutParams(-1,72));

        return root;
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
        ic.setTextSize(31);
        ic.setGravity(Gravity.CENTER);
        ic.setTextColor(greenDark());
        ic.setBackground(gradient(Color.rgb(22,184,125),Color.rgb(30,165,116),45));
        box.addView(ic,new LinearLayout.LayoutParams(62,62));
        LinearLayout tx=new LinearLayout(this);
        tx.setOrientation(LinearLayout.VERTICAL);
        tx.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        TextView t=new TextView(this);t.setText(title);t.setTextSize(19);t.setTypeface(null,Typeface.BOLD);t.setTextColor(text());t.setGravity(Gravity.RIGHT);
        TextView s=new TextView(this);s.setText(sub);s.setTextSize(11);s.setTextColor(greenDark());s.setGravity(Gravity.RIGHT);
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
        if(d.getClipData()!=null)for(int i=0;i<d.getClipData().getItemCount();i++)add(d.getClipData().getItemAt(i).getUri());
        else if(d.getData()!=null)add(d.getData());
    }

    void add(Uri u){if(selected.contains(u))return;selected.add(u);if(selectedCard!=null)selectedCard.setVisibility(View.VISIBLE);if(status!=null)status.setVisibility(View.VISIBLE);TextView t=new TextView(this);t.setText("• "+name(u));t.setTextSize(15);t.setTextColor(text());t.setGravity(Gravity.RIGHT);t.setPadding(8,11,8,11);list.addView(t);}
    String name(Uri u){try{Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null){try{if(c.moveToFirst())return c.getString(0);}finally{c.close();}}}catch(Exception ignored){}return u.toString();}

    void createOutput(){
        if(selected.isEmpty()){toast("أضف ملفًا أولًا");return;}
        progress.setVisibility(View.VISIBLE);status.setText("بدء التحويل — لا يوجد حد صفحات مصطنع...");new Thread(()->{
            try{
                StringBuilder all=new StringBuilder();int total=selected.size(),i=0;
                for(Uri u:new ArrayList<>(selected)){final int p=i*100/Math.max(1,total);runOnUiThread(()->{progress.setProgressCompat(p,true);status.setText("معالجة: "+name(u));});all.append(extract(u)).append("\n");i++;}
                File temp=new File(getCacheDir(),"Word_AlMakhlafi_"+System.currentTimeMillis()+".docx");writeDocx(temp,all.toString());pendingOutput=temp;
                runOnUiThread(()->{progress.setProgressCompat(100,true);status.setText("اكتمل التحويل. جاري الحفظ...");saveResult();});
            }catch(Exception e){runOnUiThread(()->{progress.setVisibility(View.GONE);status.setText("تعذر التحويل: "+e.getMessage());toast("تعذر التحويل: "+e.getMessage());});}
        }).start();
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
        if(n.endsWith(".docx")||n.endsWith(".xlsx")||n.endsWith(".pptx"))return extractZipXml(u);
        return "الملف بصيغة غير مدعومة.";
    }

    String ocrPdf(Uri u)throws Exception{
        File f=copyTemp(u,"pdf");PdfRenderer r=new PdfRenderer(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY));ArrayList<String> pages=new ArrayList<>();int count=r.getPageCount();
        for(int i=0;i<count;i++){final int page=i+1;runOnUiThread(()->status.setText("OCR الصفحة "+page+" من "+count));PdfRenderer.Page p=r.openPage(i);float scale=Math.min(2.4f,Math.max(1.15f,2200f/Math.max(p.getWidth(),p.getHeight())));Bitmap b=Bitmap.createBitmap(Math.max(800,(int)(p.getWidth()*scale)),Math.max(800,(int)(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();pages.add(ocrBitmap(b));b.recycle();}
        r.close();f.delete();return cleanPages(pages);
    }

    String cleanPages(ArrayList<String> pages){
        ArrayList<String> cleaned=new ArrayList<>();
        for(String s:pages) cleaned.add(normalizeText(cleanOcr(s==null?"":s)));
        if(!prefs.getBoolean("headerFooter",true)) return joinPages(cleaned);

        int n=cleaned.size();
        HashMap<String,Integer> first=new HashMap<>(), second=new HashMap<>(), last=new HashMap<>(), beforeLast=new HashMap<>();
        for(String s:cleaned){
            String[] ls=s.split("\\R");
            ArrayList<String> non=new ArrayList<>();
            for(String line:ls) if(!line.trim().isEmpty()) non.add(line.trim());
            if(non.size()>0) first.put(key(non.get(0)),first.getOrDefault(key(non.get(0)),0)+1);
            if(non.size()>1) second.put(key(non.get(1)),second.getOrDefault(key(non.get(1)),0)+1);
            if(non.size()>0){String q=key(non.get(non.size()-1));last.put(q,last.getOrDefault(q,0)+1);}
            if(non.size()>1){String q=key(non.get(non.size()-2));beforeLast.put(q,beforeLast.getOrDefault(q,0)+1);}
        }
        int threshold=Math.max(2,(n+1)/2);
        HashSet<String> repeatedTop=new HashSet<>(), repeatedBottom=new HashSet<>();
        for(Map.Entry<String,Integer> e:first.entrySet()) if(e.getValue()>=threshold&&!e.getKey().isEmpty()) repeatedTop.add(e.getKey());
        for(Map.Entry<String,Integer> e:second.entrySet()) if(e.getValue()>=threshold&&!e.getKey().isEmpty()) repeatedTop.add(e.getKey());
        for(Map.Entry<String,Integer> e:last.entrySet()) if(e.getValue()>=threshold&&!e.getKey().isEmpty()) repeatedBottom.add(e.getKey());
        for(Map.Entry<String,Integer> e:beforeLast.entrySet()) if(e.getValue()>=threshold&&!e.getKey().isEmpty()) repeatedBottom.add(e.getKey());

        ArrayList<String> out=new ArrayList<>();
        for(String s:cleaned){
            String[] ls=s.split("\\R",-1);
            StringBuilder bld=new StringBuilder();
            for(int i=0;i<ls.length;i++){
                String line=ls[i].trim();
                if(line.isEmpty()) continue;
                String k=key(line);
                boolean edgeTop=i<=1, edgeBottom=i>=ls.length-2;
                boolean skip=(edgeTop&&repeatedTop.contains(k))||(edgeBottom&&repeatedBottom.contains(k))||(isPageNumber(line)&&(edgeTop||edgeBottom));
                if(!skip)bld.append(line).append("\\n");
            }
            out.add(bld.toString().trim());
        }
        return joinPages(out);
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
        File td=new File(getFilesDir(),"tessdata");if(!td.exists())td.mkdirs();asset("tessdata/ara.traineddata",new File(td,"ara.traineddata"));asset("tessdata/eng.traineddata",new File(td,"eng.traineddata"));
        TessBaseAPI t=new TessBaseAPI();if(!t.init(getFilesDir().getAbsolutePath(),"ara+eng"))throw new Exception("فشل تشغيل OCR");t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);t.setImage(b);String x=t.getUTF8Text();t.recycle();return normalizeText(cleanOcr(x==null?"":x));
    }

    String cleanOcr(String x){
        if(x==null) return "";
        StringBuilder s=new StringBuilder();
        for(int i=0;i<x.length();i++){
            char c=x.charAt(i);
            boolean arabic=(c>='\u0600'&&c<='\u06FF')||(c>='\u0750'&&c<='\u077F')||(c>='\u08A0'&&c<='\u08FF');
            boolean digit=(c>='0'&&c<='9')||(c>='\u0660'&&c<='\u0669')||(c>='\u06F0'&&c<='\u06F9');
            boolean punctuation=" ،؛:،.؟!?-_/()[]{}%+*=\\\"'".indexOf(c)>=0;
            if(Character.isWhitespace(c)||arabic||digit||punctuation) s.append(c);
            else if(c=='\u00ad'||c=='\u200b'||c=='\ufeff'||(c>='A'&&c<='Z')||(c>='a'&&c<='z')) s.append(' ');
        }
        String q=s.toString().replaceAll("[ ]{2,}"," ");
        q=q.replaceAll("(?m)^[ ]+$","").replaceAll("(?m)^[^\\u0600-\\u06FF0-9٠-٩]+$","");
        return q;
    }

    String normalizeText(String x){
        if(x==null)return "";
        String s=x.replace("ـ","");
        // Conservative Arabic OCR/spelling cleanup: correct frequent OCR forms without rewriting the document.
        String[] bad={"هاذا","هاذه","هاؤلاء","اللذي","اللذين","الذيي","التيي","لاكن","ولكنن","مسوول","مسئول","شيى","شئ","جزءا"};
        String[] good={"هذا","هذه","هؤلاء","الذي","اللذين","الذي","التي","لكن","ولكن","مسؤول","مسؤول","شيء","شيء","جزءاً"};
        for(int i=0;i<bad.length;i++) s=s.replaceAll("(?<![\\u0600-\\u06FF])"+bad[i]+"(?![\\u0600-\\u06FF])",good[i]);
        s=s.replaceAll("[ \\t]+"," ").replaceAll(" *\\n *","\\n");
        s=s.replaceAll(" +([،؛:؟,.!])","$1");
        s=s.replaceAll("[ ]{2,}"," ");
        return s.trim();
    }

    void asset(String a,File d)throws Exception{if(d.exists()&&d.length()>1000)return;InputStream in=getAssets().open(a);FileOutputStream o=new FileOutputStream(d);byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);in.close();o.close();}
    String extractZipXml(Uri u)throws Exception{
        File f=copyTemp(u,"zip");ZipFile z=new ZipFile(f);StringBuilder s=new StringBuilder();Enumeration<? extends ZipEntry> es=z.entries();
        while(es.hasMoreElements()){ZipEntry e=es.nextElement();String n=e.getName();if(!n.endsWith(".xml")||!(n.contains("word/")||n.contains("xl/")||n.contains("ppt/")))continue;InputStream in=z.getInputStream(e);Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);NodeList ts=d.getElementsByTagNameNS("*","t");for(int i=0;i<ts.getLength();i++)s.append(ts.item(i).getTextContent()).append(" ");s.append("\n");in.close();}z.close();f.delete();return normalizeText(s.toString());
    }

    File copyTemp(Uri u,String ext)throws Exception{File f=new File(getCacheDir(),System.currentTimeMillis()+"."+ext);InputStream in=getContentResolver().openInputStream(u);FileOutputStream o=new FileOutputStream(f);copyStream(in,o);return f;}
    void writeDocx(File out,String text)throws Exception{
        ZipOutputStream z=new ZipOutputStream(new FileOutputStream(out));
        put(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
        put(z,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
        put(z,"word/_rels/document.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>");
        StringBuilder b=new StringBuilder("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        for(String line:text.split("\\n",-1)){if(line.trim().isEmpty())continue;b.append("<w:p><w:pPr>");if(prefs.getBoolean("rtl",true))b.append("<w:bidi/>");b.append("<w:jc w:val=\"right\"/><w:spacing w:after=\"80\"/></w:pPr><w:r><w:t xml:space=\"preserve\">").append(xml(line.trim())).append("</w:t></w:r></w:p>");}
        b.append("<w:sectPr><w:pgMar w:top=\"720\" w:right=\"900\" w:bottom=\"720\" w:left=\"900\"/></w:sectPr></w:body></w:document>");put(z,"word/document.xml",b.toString());z.close();
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
