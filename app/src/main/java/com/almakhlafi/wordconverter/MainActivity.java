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
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(14,14,14,10); root.setBackgroundColor(bg());

        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackground(gradient(greenDark(),green(),30)); header.setPadding(18,10,12,10);
        LinearLayout titleBox=new LinearLayout(this); titleBox.setOrientation(LinearLayout.VERTICAL); titleBox.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=new TextView(this); title.setText("W-المخلافي"); title.setTextSize(27); title.setTypeface(null,1); title.setTextColor(Color.WHITE); title.setGravity(Gravity.RIGHT);
        TextView subTitle=new TextView(this); subTitle.setText("تحويل المستندات إلى Word قابل للتحرير"); subTitle.setTextSize(13); subTitle.setTextColor(Color.WHITE); subTitle.setGravity(Gravity.RIGHT);
        titleBox.addView(title); titleBox.addView(subTitle);
        header.addView(titleBox,new LinearLayout.LayoutParams(0,82,1));
        MaterialButton settings=new MaterialButton(this); settings.setText("⚙"); settings.setTextSize(22); settings.setTextColor(Color.WHITE); settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setOnClickListener(v->settingsDialog()); header.addView(settings,new LinearLayout.LayoutParams(64,64));
        root.addView(header);

        MaterialCardView dhikr=card(); dhikr.setCardBackgroundColor(Color.rgb(230,249,241));
        TextView d=new TextView(this); d.setText("ﷺ  صلِّ على محمد صلى الله عليه وسلم وعلى آله الطيبين الطاهرين  ﷺ"); d.setTextSize(17); d.setTypeface(null,1); d.setTextColor(greenDark()); d.setGravity(Gravity.CENTER); d.setPadding(12,15,12,15);
        dhikr.addView(d); root.addView(dhikr);

        LinearLayout primary=new LinearLayout(this); primary.setOrientation(LinearLayout.HORIZONTAL); primary.setGravity(Gravity.CENTER); primary.setPadding(0,8,0,8);
        MaterialButton camera=bigGreenButton("📷  المسح الضوئي"); camera.setOnClickListener(v->camera());
        MaterialButton importBtn=bigGreenButton("📂  استيراد الملفات"); importBtn.setOnClickListener(v->pick());
        primary.addView(camera,new LinearLayout.LayoutParams(0,64,1)); primary.addView(importBtn,new LinearLayout.LayoutParams(0,64,1));
        root.addView(primary);

        LinearLayout menuRow=new LinearLayout(this); menuRow.setGravity(Gravity.CENTER); menuRow.setPadding(0,2,0,6);
        MaterialButton convert=menuButton("تحويلات ▾"); convert.setOnClickListener(v->conversionMenu(convert));
        MaterialButton more=menuButton("المزيد ▾"); more.setOnClickListener(v->moreMenu(more));
        menuRow.addView(convert,new LinearLayout.LayoutParams(0,58,1)); menuRow.addView(more,new LinearLayout.LayoutParams(0,58,1));
        root.addView(menuRow);

        MaterialCardView filesCard=card();
        LinearLayout filesBox=new LinearLayout(this); filesBox.setOrientation(LinearLayout.VERTICAL); filesBox.setPadding(12,8,12,8);
        TextView fh=sectionTitle("الملفات المحددة");
        filesBox.addView(fh);
        ScrollView sv=new ScrollView(this); list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(4,4,4,4); sv.addView(list);
        filesBox.addView(sv,new LinearLayout.LayoutParams(-1,0,1)); filesCard.addView(filesBox);
        root.addView(filesCard,new LinearLayout.LayoutParams(-1,0,1));

        progress=new LinearProgressIndicator(this); progress.setMax(100); progress.setVisibility(View.GONE); root.addView(progress);
        status=new TextView(this); status.setText("جاهز — اختر المسح الضوئي أو استيراد الملفات."); status.setTextColor(text()); status.setGravity(Gravity.RIGHT); status.setPadding(6,8,6,6); root.addView(status);

        LinearLayout bottom=new LinearLayout(this); bottom.setGravity(Gravity.CENTER);
        MaterialButton start=bigGreenButton("▶  بدء التحويل"); start.setOnClickListener(v->createOutput());
        MaterialButton clear=menuButton("مسح القائمة"); clear.setOnClickListener(v->{selected.clear();list.removeAllViews();status.setText("تم مسح القائمة.");});
        bottom.addView(clear,new LinearLayout.LayoutParams(0,60,1)); bottom.addView(start,new LinearLayout.LayoutParams(0,60,1)); root.addView(bottom);
        return root;
    }

    MaterialButton bigGreenButton(String s){MaterialButton b=menuButton(s);b.setTextColor(Color.WHITE);b.setBackground(gradient(greenDark(),green(),22));b.setStrokeWidth(0);return b;}
    MaterialButton menuButton(String s){MaterialButton b=new MaterialButton(this);b.setText(s);b.setTextSize(15);b.setAllCaps(false);b.setTextColor(text());b.setGravity(Gravity.CENTER);b.setCornerRadius(18);b.setStrokeWidth(1);b.setStrokeColor(android.content.res.ColorStateList.valueOf(green()));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,58);p.setMargins(5,4,5,4);b.setLayoutParams(p);return b;}
    MaterialCardView card(){MaterialCardView c=new MaterialCardView(this);c.setRadius(24);c.setCardElevation(8);c.setUseCompatPadding(true);c.setCardBackgroundColor(isDark()?Color.rgb(30,45,38):Color.WHITE);return c;}
    TextView sectionTitle(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(18);t.setTypeface(null,1);t.setTextColor(text());t.setGravity(Gravity.RIGHT);t.setPadding(6,4,6,8);return t;}
    GradientDrawable gradient(int c1,int c2,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{c1,c2});g.setCornerRadius(radius);return g;}

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

    void add(Uri u){if(selected.contains(u))return;selected.add(u);TextView t=new TextView(this);t.setText("• "+name(u));t.setTextSize(15);t.setTextColor(text());t.setGravity(Gravity.RIGHT);t.setPadding(8,11,8,11);list.addView(t);}
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
        ArrayList<String> cleaned=new ArrayList<>();for(String s:pages)cleaned.add(normalizeText(s));
        if(!prefs.getBoolean("headerFooter",true))return joinPages(cleaned);
        HashMap<String,Integer> first=new HashMap<>(),last=new HashMap<>();int n=cleaned.size();
        for(String s:cleaned){String[] ls=s.split("\\R");if(ls.length>0){String a=key(ls[0]);if(!a.isEmpty())first.put(a,first.getOrDefault(a,0)+1);String z=key(ls[ls.length-1]);if(!z.isEmpty())last.put(z,last.getOrDefault(z,0)+1);}}
        HashSet<String> fh=new HashSet<>(),ft=new HashSet<>();for(Map.Entry<String,Integer> e:first.entrySet())if(e.getValue()>=Math.max(2,(n+1)/2))fh.add(e.getKey());for(Map.Entry<String,Integer> e:last.entrySet())if(e.getValue()>=Math.max(2,(n+1)/2))ft.add(e.getKey());
        for(String s:cleaned){String[] ls=s.split("\\R",-1);StringBuilder b=new StringBuilder();for(int i=0;i<ls.length;i++){String k=key(ls[i]);boolean skip=(i==0&&fh.contains(k))||(i==ls.length-1&&ft.contains(k))||(isPageNumber(ls[i])&&(i==0||i==ls.length-1));if(!skip)b.append(ls[i]).append("\n");}cleaned.add(0,"");break;}
        // Rebuild without modifying while iterating.
        ArrayList<String> out=new ArrayList<>();for(String s:cleaned){if(s.isEmpty()&&out.isEmpty())continue;out.add(s);}
        return joinPages(out);
    }

    String joinPages(ArrayList<String> pages){StringBuilder b=new StringBuilder();for(String s:pages){if(b.length()>0)b.append("\n");b.append(s.trim());}return b.toString();}
    String key(String s){return s.replaceAll("[\\s\\p{Punct}]+","").trim();}
    boolean isPageNumber(String s){return s.trim().matches("[0-9٠-٩]{1,5}");}

    Bitmap decodeScaled(Uri u)throws Exception{InputStream in=getContentResolver().openInputStream(u);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeStream(in,null,o);in.close();int max=2800,sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>max)sample*=2;in=getContentResolver().openInputStream(u);o.inJustDecodeBounds=false;o.inSampleSize=sample;Bitmap b=BitmapFactory.decodeStream(in,null,o);in.close();return b;}

    String ocrBitmap(Bitmap b)throws Exception{
        if(b==null)throw new Exception("تعذر قراءة الصورة");
        File td=new File(getFilesDir(),"tessdata");if(!td.exists())td.mkdirs();asset("tessdata/ara.traineddata",new File(td,"ara.traineddata"));asset("tessdata/eng.traineddata",new File(td,"eng.traineddata"));
        TessBaseAPI t=new TessBaseAPI();if(!t.init(getFilesDir().getAbsolutePath(),"ara+eng"))throw new Exception("فشل تشغيل OCR");t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);t.setImage(b);String x=t.getUTF8Text();t.recycle();return normalizeText(cleanOcr(x==null?"":x));
    }

    String cleanOcr(String x){if(!prefs.getBoolean("clean",true))return x;StringBuilder s=new StringBuilder();for(int i=0;i<x.length();i++){char c=x.charAt(i);boolean ok=Character.isLetterOrDigit(c)||Character.isWhitespace(c)||" ،؛:,.!?؟-_/()[]{}%+*=\"'".indexOf(c)>=0;if(ok)s.append(c);else if(c=='\u00ad'||c=='\u200b'||c=='\ufeff'){}else s.append(' ');}return s.toString().replaceAll("[ ]{2,}"," ");}

    String normalizeText(String x){
        if(x==null)return "";
        String s=x.replace('أ','ا').replace('إ','ا').replace('آ','ا').replace('ٱ','ا').replace('ـ','');
        s=s.replaceAll("[ \\t]+"," ").replaceAll(" *\\n *","\\n");
        s=s.replaceAll(" +([،؛:؟,.!])","$1");
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
