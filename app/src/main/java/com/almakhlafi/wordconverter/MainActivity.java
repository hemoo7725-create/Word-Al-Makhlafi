package com.almakhlafi.wordconverter;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.FileProvider;
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
    static final int PICK=100, CREATE=200, CAMERA=300;
    LinearLayout list, libraryList, root;
    TextView status;
    LinearProgressIndicator progress;
    ArrayList<Uri> selected=new ArrayList<>();
    File pendingOutput;
    int pdfMode=0; // 0=all, 1=1-20, 2=21-50, 3=51+
    SharedPreferences prefs;

    @Override public void onCreate(Bundle b){
        prefs=getSharedPreferences("settings",MODE_PRIVATE);
        applyTheme();
        super.onCreate(b);
        setContentView(ui());
        refreshLibrary();
    }

    void applyTheme(){
        boolean dark=prefs!=null && prefs.getBoolean("dark",false);
        AppCompatDelegate.setDefaultNightMode(dark?AppCompatDelegate.MODE_NIGHT_YES:AppCompatDelegate.MODE_NIGHT_NO);
    }

    int bg(){return isDark()?Color.rgb(18,24,31):Color.rgb(244,247,251);}
    int text(){return isDark()?Color.WHITE:Color.rgb(30,40,50);}
    boolean isDark(){return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;}

    View ui(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,18,18,18); root.setBackgroundColor(bg());

        LinearLayout header=new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable hd=gradient(Color.rgb(20,55,90),Color.rgb(45,121,185),28);
        header.setBackground(hd); header.setPadding(18,12,18,12);
        TextView title=new TextView(this); title.setText("W-المخلافي"); title.setTextSize(27); title.setTextColor(Color.WHITE); title.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        header.addView(title,new LinearLayout.LayoutParams(0,86,1));
        MaterialButton settings=new MaterialButton(this); settings.setText("⚙"); settings.setTextSize(22); settings.setTextColor(Color.WHITE); settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setOnClickListener(v->settingsDialog()); header.addView(settings,new LinearLayout.LayoutParams(70,70));
        root.addView(header);

        TextView sub=new TextView(this); sub.setText("محول المستندات الذكي إلى Word قابل للتحرير"); sub.setTextSize(16); sub.setTextColor(text()); sub.setGravity(Gravity.RIGHT); sub.setPadding(4,14,4,10); root.addView(sub);

        HorizontalScrollView hs=new HorizontalScrollView(this); LinearLayout quick=new LinearLayout(this); quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.addView(actionButton("📄 PDF حتى 20 صفحة",v->pickPdf(1)));
        quick.addView(actionButton("📑 PDF 21–50 صفحة",v->pickPdf(2)));
        quick.addView(actionButton("📚 PDF 51+ صفحة",v->pickPdf(3)));
        quick.addView(actionButton("🖼 صور إلى Word",v->pickImages()));
        quick.addView(actionButton("📷 الكاميرا",v->camera()));
        hs.addView(quick); root.addView(hs,new LinearLayout.LayoutParams(-1,70));

        LinearLayout bar=new LinearLayout(this); bar.setGravity(Gravity.RIGHT);
        MaterialButton add=new MaterialButton(this); add.setText("➕ إضافة ملفات");
        MaterialButton library=new MaterialButton(this); library.setText("📚 الحافظة");
        MaterialButton clear=new MaterialButton(this); clear.setText("مسح");
        bar.addView(clear); bar.addView(library); bar.addView(add); root.addView(bar);

        MaterialCardView conversionCard=card();
        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.VERTICAL); actions.setPadding(12,10,12,10);
        actions.addView(sectionTitle("أوامر التحويل"));
        actions.addView(actionButton("📄 تحويل PDF إلى Word",v->{pdfMode=0;pick();}));
        actions.addView(actionButton("🖼 تحويل PDF المصور OCR إلى Word",v->{pdfMode=0;pick();}));
        actions.addView(actionButton("📊 Excel إلى Word مع البيانات والجداول",v->{pdfMode=0;pick();}));
        actions.addView(actionButton("📝 Word إلى Word قابل للتحرير",v->{pdfMode=0;pick();}));
        actions.addView(actionButton("📊 PowerPoint إلى Word",v->{pdfMode=0;pick();}));
        actions.addView(actionButton("📦 تحويل ملفات متعددة دفعة واحدة",v->{pdfMode=0;pick();}));
        conversionCard.addView(actions); root.addView(conversionCard);

        ScrollView sv=new ScrollView(this); list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(4,4,4,4); sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));

        progress=new LinearProgressIndicator(this); progress.setMax(100); progress.setVisibility(View.GONE); root.addView(progress);
        status=new TextView(this); status.setText("جاهز — اختر الملفات التي تريد تحويلها."); status.setTextColor(text()); status.setGravity(Gravity.RIGHT); status.setPadding(4,10,4,8); root.addView(status);

        MaterialButton go=new MaterialButton(this); go.setText("🚀 بدء التحويل إلى Word"); go.setTextSize(16); go.setBackground(gradient(Color.rgb(25,91,145),Color.rgb(52,137,201),22));
        go.setTextColor(Color.WHITE); root.addView(go);
        add.setOnClickListener(v->pick()); library.setOnClickListener(v->showLibrary()); clear.setOnClickListener(v->{selected.clear();list.removeAllViews();status.setText("تم مسح القائمة.");});
        go.setOnClickListener(v->createOutput());
        return root;
    }

    MaterialCardView card(){
        MaterialCardView c=new MaterialCardView(this); c.setRadius(24); c.setCardElevation(12); c.setUseCompatPadding(true);
        c.setCardBackgroundColor(isDark()?Color.rgb(30,39,48):Color.WHITE); return c;
    }
    TextView sectionTitle(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(19);t.setTextColor(text());t.setGravity(Gravity.RIGHT);t.setPadding(6,4,6,10);return t;}
    MaterialButton actionButton(String s,View.OnClickListener l){
        MaterialButton b=new MaterialButton(this); b.setText(s); b.setTextSize(14); b.setAllCaps(false); b.setTextColor(text()); b.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        b.setOnClickListener(l); b.setCornerRadius(18); b.setStrokeWidth(1); b.setStrokeColor(android.content.res.ColorStateList.valueOf(Color.rgb(80,130,170)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,58);p.setMargins(5,5,5,5);b.setLayoutParams(p);return b;
    }
    GradientDrawable gradient(int c1,int c2,int radius){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{c1,c2});g.setCornerRadius(radius);return g;}

    void settingsDialog(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(28,8,28,8);
        TextView info=new TextView(this);info.setText("إعدادات Word Al-Makhlafi");info.setTextSize(21);info.setTextColor(text());info.setGravity(Gravity.RIGHT);box.addView(info);
        Switch dark=new Switch(this);dark.setText("الوضع الداكن");dark.setTextSize(16);dark.setGravity(Gravity.RIGHT);dark.setChecked(prefs.getBoolean("dark",false));box.addView(dark);
        Switch clean=new Switch(this);clean.setText("تنقية رموز OCR غير الطبيعية");clean.setTextSize(16);clean.setGravity(Gravity.RIGHT);clean.setChecked(prefs.getBoolean("clean",true));box.addView(clean);
        Switch rtl=new Switch(this);rtl.setText("تنسيق Word عربي من اليمين إلى اليسار");rtl.setTextSize(16);rtl.setGravity(Gravity.RIGHT);rtl.setChecked(prefs.getBoolean("rtl",true));box.addView(rtl);
        new AlertDialog.Builder(this).setView(box).setPositiveButton("حفظ", (d,w)->{
            prefs.edit().putBoolean("dark",dark.isChecked()).putBoolean("clean",clean.isChecked()).putBoolean("rtl",rtl.isChecked()).apply();
            applyTheme(); recreate();
        }).setNegativeButton("إلغاء",null).show();
    }

    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.setType("*/*");startActivityForResult(i,PICK);}
    void pickPdf(int mode){pdfMode=mode;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("application/pdf");startActivityForResult(i,PICK);}
    void pickImages(){pdfMode=0;Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.setType("image/*");startActivityForResult(i,PICK);}
    void camera(){
        try{
            File dir=new File(getExternalFilesDir("Camera"),"captures");if(!dir.exists())dir.mkdirs();
            File f=new File(dir,"capture_"+System.currentTimeMillis()+".jpg");
            getSharedPreferences("camera",0).edit().putString("path",f.getAbsolutePath()).apply();
            Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);
            Intent i=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);i.putExtra(MediaStore.EXTRA_OUTPUT,u);i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivityForResult(i,CAMERA);
        }catch(Exception e){toast("تعذر فتح الكاميرا: "+e.getMessage());}
    }

    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);
        if(r==CREATE){if(c==RESULT_OK&&d!=null&&d.getData()!=null)savePendingTo(d.getData());else status.setText("تم حفظ النسخة داخل مستندات التطبيق.");return;}
        if(r==CAMERA){if(c==RESULT_OK){String p=getSharedPreferences("camera",0).getString("path",null);if(p!=null){File f=new File(p);if(f.exists()){Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);add(u);}}}return;}
        if(c!=RESULT_OK||d==null||r!=PICK)return;
        if(d.getClipData()!=null){for(int i=0;i<d.getClipData().getItemCount();i++)add(d.getClipData().getItemAt(i).getUri());}
        else if(d.getData()!=null)add(d.getData());
    }

    void add(Uri u){if(selected.contains(u))return;selected.add(u);TextView t=new TextView(this);t.setText("• "+name(u));t.setTextSize(15);t.setTextColor(text());t.setGravity(Gravity.RIGHT);t.setPadding(8,13,8,13);list.addView(t);}
    String name(Uri u){try{Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null){try{if(c.moveToFirst())return c.getString(0);}finally{c.close();}}}catch(Exception ignored){}return u.toString();}
    
    void createOutput(){
        if(selected.isEmpty()){toast("أضف ملفًا أولاً");return;}
        progress.setVisibility(View.VISIBLE);status.setText("بدء التحويل...");new Thread(()->{
            try{
                StringBuilder all=new StringBuilder();int total=selected.size(),i=0;
                for(Uri u:new ArrayList<>(selected)){
                    final int p=i*100/Math.max(1,total);runOnUiThread(()->{progress.setProgressCompat(p,true);status.setText("معالجة: "+name(u));});
                    all.append("===== ").append(name(u)).append(" =====\n").append(extract(u)).append("\n");i++;
                }
                File temp=new File(getCacheDir(),"Word_AlMakhlafi_"+System.currentTimeMillis()+".docx");writeDocx(temp,all.toString());
                File lib=libraryDir();File saved=new File(lib,"Word_"+System.currentTimeMillis()+".docx");copyFile(temp,saved);pendingOutput=temp;
                runOnUiThread(()->{progress.setProgressCompat(100,true);status.setText("اكتمل التحويل. اختر الآن مكان حفظ الملف.");refreshLibrary();askSaveLocation();});
            }catch(Exception e){runOnUiThread(()->{progress.setVisibility(View.GONE);status.setText("تعذر التحويل: "+e.getMessage());toast("تعذر التحويل: "+e.getMessage());});}
        }).start();
    }

    void askSaveLocation(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");i.putExtra(Intent.EXTRA_TITLE,"Word_Al-Makhlafi_Converted.docx");startActivityForResult(i,CREATE);}
    void savePendingTo(Uri out){
        if(pendingOutput==null)return;try{OutputStream o=getContentResolver().openOutputStream(out);copyStream(new FileInputStream(pendingOutput),o);pendingOutput.delete();progress.setVisibility(View.GONE);status.setText("تم حفظ الملف بنجاح في المكان الذي اخترته.");toast("تم الحفظ بنجاح.");}catch(Exception e){toast("تعذر الحفظ: "+e.getMessage());}}
    
    String extract(Uri u)throws Exception{
        String n=name(u).toLowerCase(Locale.ROOT);
        if(n.endsWith(".pdf"))return ocrPdf(u);
        if(n.matches(".*\\.(jpg|jpeg|png|bmp|tif|tiff|webp)$"))return ocrBitmap(decodeScaled(u));
        if(n.endsWith(".docx")||n.endsWith(".xlsx")||n.endsWith(".pptx"))return extractZipXml(u);
        return "الملف بصيغة غير مدعومة. استخدم PDF أو الصور أو DOCX/XLSX/PPTX.";
    }

    String ocrPdf(Uri u)throws Exception{
        File f=copyTemp(u,"pdf");PdfRenderer r=new PdfRenderer(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY));StringBuilder s=new StringBuilder();
        int pages=r.getPageCount();int start=0,end=pages;
        if(pdfMode==1){start=0;end=Math.min(20,pages);} else if(pdfMode==2){start=Math.min(20,pages);end=Math.min(50,pages);} else if(pdfMode==3){start=Math.min(50,pages);end=pages;}
        for(int i=start;i<end;i++){
            final int page=i+1;runOnUiThread(()->status.setText("OCR صفحة "+page+" من "+pages));
            PdfRenderer.Page p=r.openPage(i);
            float scale=Math.min(2.0f,Math.max(1.0f,1800f/Math.max(p.getWidth(),p.getHeight())));
            Bitmap b=Bitmap.createBitmap(Math.max(800,(int)(p.getWidth()*scale)),Math.max(800,(int)(p.getHeight()*scale)),Bitmap.Config.ARGB_8888);
            b.eraseColor(Color.WHITE);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();
            s.append(ocrBitmap(b)).append("\n");b.recycle();
        }
        r.close();f.delete();return s.toString();
    }

    Bitmap decodeScaled(Uri u)throws Exception{
        InputStream in=getContentResolver().openInputStream(u);BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeStream(in,null,o);in.close();
        int max=2600, sample=1;while(Math.max(o.outWidth,o.outHeight)/sample>max)sample*=2;
        in=getContentResolver().openInputStream(u);o.inJustDecodeBounds=false;o.inSampleSize=sample;Bitmap b=BitmapFactory.decodeStream(in,null,o);in.close();return b;
    }

    String ocrBitmap(Bitmap b)throws Exception{
        if(b==null)throw new Exception("تعذر قراءة الصورة");
        File td=new File(getFilesDir(),"tessdata");if(!td.exists())td.mkdirs();asset("tessdata/ara.traineddata",new File(td,"ara.traineddata"));asset("tessdata/eng.traineddata",new File(td,"eng.traineddata"));
        TessBaseAPI t=new TessBaseAPI();if(!t.init(getFilesDir().getAbsolutePath(),"ara+eng"))throw new Exception("فشل تشغيل OCR");
        t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_AUTO);t.setImage(b);String x=t.getUTF8Text();t.recycle();return cleanOcr(x==null?"":x);
    }
    String cleanOcr(String x){
        if(!prefs.getBoolean("clean",true))return x;
        StringBuilder s=new StringBuilder();for(int i=0;i<x.length();i++){char c=x.charAt(i);
            boolean ok=Character.isLetterOrDigit(c)||Character.isWhitespace(c)||" ،؛:,.!?؟؛-_/()[]{}%+*=\"'".indexOf(c)>=0;
            if(ok)s.append(c);else if(c=='\u00ad'||c=='\u200b'||c=='\ufeff'){}else s.append(' ');
        }return s.toString().replaceAll("[ ]{2,}"," ");
    }

    void asset(String a,File d)throws Exception{if(d.exists()&&d.length()>1000)return;InputStream in=getAssets().open(a);FileOutputStream o=new FileOutputStream(d);byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);in.close();o.close();}
    String extractZipXml(Uri u)throws Exception{
        File f=copyTemp(u,"zip");ZipFile z=new ZipFile(f);StringBuilder s=new StringBuilder();Enumeration<? extends ZipEntry> es=z.entries();
        while(es.hasMoreElements()){ZipEntry e=es.nextElement();String n=e.getName();if(!n.endsWith(".xml")||!(n.contains("word/")||n.contains("xl/")||n.contains("ppt/")))continue;
            InputStream in=z.getInputStream(e);Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);NodeList ts=d.getElementsByTagNameNS("*","t");for(int i=0;i<ts.getLength();i++)s.append(ts.item(i).getTextContent()).append(" ");s.append("\n");in.close();}
        z.close();f.delete();return s.toString();
    }

    File copyTemp(Uri u,String ext)throws Exception{File f=new File(getCacheDir(),System.currentTimeMillis()+"."+ext);InputStream in=getContentResolver().openInputStream(u);FileOutputStream o=new FileOutputStream(f);copyStream(in,o);return f;}
    void writeDocx(File out,String text)throws Exception{
        ZipOutputStream z=new ZipOutputStream(new FileOutputStream(out));
        put(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
        put(z,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
        put(z,"word/_rels/document.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>");
        StringBuilder b=new StringBuilder("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        for(String line:text.split("\\n",-1)){b.append("<w:p><w:pPr>");if(prefs.getBoolean("rtl",true))b.append("<w:bidi/>");b.append("</w:pPr><w:r><w:t xml:space=\"preserve\">").append(xml(line)).append("</w:t></w:r></w:p>");}
        b.append("<w:sectPr/></w:body></w:document>");put(z,"word/document.xml",b.toString());z.close();
    }
    void put(ZipOutputStream z,String n,String s)throws Exception{z.putNextEntry(new ZipEntry(n));z.write(s.getBytes("UTF-8"));z.closeEntry();}
    String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    void copyFile(File a,File b)throws Exception{copyStream(new FileInputStream(a),new FileOutputStream(b));}
    void copyStream(InputStream in,OutputStream out)throws Exception{try{byte[] b=new byte[65536];int n;while((n=in.read(b))>0)out.write(b,0,n);}finally{try{in.close();}catch(Exception ignored){}try{out.close();}catch(Exception ignored){}}}

    File libraryDir(){File d=new File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS),"WordAlMakhlafi");if(!d.exists())d.mkdirs();return d;}
    void refreshLibrary(){
        if(root==null)return;
        // The library is shown in a compact dialog from the main screen button.
        // Keep the main conversion area uncluttered.
    }
    void showLibrary(){
        File[] fs=libraryDir().listFiles((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".docx"));
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(20,10,20,10);
        TextView h=new TextView(this);h.setText("حافظة المستندات");h.setTextSize(21);h.setTextColor(text());h.setGravity(Gravity.RIGHT);box.addView(h);
        if(fs==null||fs.length==0){TextView e=new TextView(this);e.setText("لا توجد مستندات محفوظة بعد.");e.setGravity(Gravity.RIGHT);e.setPadding(5,20,5,20);box.addView(e);}
        else for(File f:fs){MaterialButton b=new MaterialButton(this);b.setText("📄 "+f.getName());b.setAllCaps(false);b.setOnClickListener(v->openDoc(f));box.addView(b);}
        new AlertDialog.Builder(this).setView(box).setPositiveButton("إغلاق",null).show();
    }
    void openDoc(File f){try{Uri u=FileProvider.getUriForFile(this,getPackageName()+".fileprovider",f);Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,"application/vnd.openxmlformats-officedocument.wordprocessingml.document");i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){toast("لا يوجد تطبيق لفتح Word.");}}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
