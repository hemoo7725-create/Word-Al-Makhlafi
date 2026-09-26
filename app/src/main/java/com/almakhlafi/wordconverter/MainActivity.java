package com.almakhlafi.wordconverter;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.googlecode.tesseract.android.TessBaseAPI;
import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

public class MainActivity extends AppCompatActivity {
    static final int PICK=100, CREATE=200;
    LinearLayout list; TextView status; LinearProgressIndicator progress;
    ArrayList<Uri> selected=new ArrayList<>();

    @Override public void onCreate(Bundle b){super.onCreate(b);setContentView(ui());}

    View ui(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(24,24,24,20);root.setBackgroundColor(Color.rgb(244,247,251));
        TextView h=new TextView(this);h.setText("Word Al-Makhlafi");h.setTextSize(28);h.setTextColor(Color.WHITE);h.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);h.setPadding(18,0,18,0);h.setBackgroundColor(Color.rgb(23,50,77));root.addView(h,new LinearLayout.LayoutParams(-1,110));
        TextView sub=new TextView(this);sub.setText("تحويل PDF والصور وWord وExcel وPowerPoint إلى Word قابل للتحرير");sub.setTextSize(16);sub.setGravity(Gravity.RIGHT);sub.setPadding(0,16,0,12);root.addView(sub);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.RIGHT);
        MaterialButton add=new MaterialButton(this);add.setText("➕ إضافة ملفات");MaterialButton clear=new MaterialButton(this);clear.setText("مسح");
        bar.addView(clear);bar.addView(add);root.addView(bar);
        ScrollView sv=new ScrollView(this);list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));
        progress=new LinearProgressIndicator(this);progress.setMax(100);progress.setVisibility(View.GONE);root.addView(progress);
        status=new TextView(this);status.setText("جاهز — اختر الملفات التي تريد تحويلها.");status.setGravity(Gravity.RIGHT);status.setPadding(0,10,0,10);root.addView(status);
        MaterialButton go=new MaterialButton(this);go.setText("🚀 تحويل إلى Word");root.addView(go);
        add.setOnClickListener(v->pick());clear.setOnClickListener(v->{selected.clear();list.removeAllViews();status.setText("تم مسح القائمة.");});go.setOnClickListener(v->createOutput());
        return root;
    }
    void pick(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);i.setType("*/*");startActivityForResult(i,PICK);}
    @Override protected void onActivityResult(int r,int c,Intent d){
        super.onActivityResult(r,c,d);if(c!=RESULT_OK||d==null)return;
        if(r==CREATE){doConversion(d.getData());return;}
        if(r!=PICK)return;
        if(d.getClipData()!=null){for(int i=0;i<d.getClipData().getItemCount();i++)add(d.getClipData().getItemAt(i).getUri());}
        else if(d.getData()!=null)add(d.getData());
    }
    void add(Uri u){if(selected.contains(u))return;selected.add(u);TextView t=new TextView(this);t.setText("• "+name(u));t.setTextSize(15);t.setGravity(Gravity.RIGHT);t.setPadding(8,13,8,13);list.addView(t);}
    String name(Uri u){Cursor c=getContentResolver().query(u,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null);if(c!=null){try{if(c.moveToFirst())return c.getString(0);}finally{c.close();}}return u.toString();}
    void createOutput(){if(selected.isEmpty()){toast("أضف ملفًا أولاً");return;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.setType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");i.putExtra(Intent.EXTRA_TITLE,"Word_Al-Makhlafi_Converted.docx");startActivityForResult(i,CREATE);}
    void doConversion(Uri outUri){
        progress.setVisibility(View.VISIBLE);new Thread(()->{try{
            StringBuilder all=new StringBuilder();int total=selected.size(),i=0;
            for(Uri u:selected){final int p=i*100/total;runOnUiThread(()->{progress.setProgressCompat(p,true);status.setText("معالجة: "+name(u));});all.append("\n===== ").append(name(u)).append(" =====\n").append(extract(u)).append("\n");i++;}
            writeDocx(outUri,all.toString());runOnUiThread(()->{progress.setProgressCompat(100,true);status.setText("تم إنشاء ملف Word بنجاح.");toast("تم التحويل بنجاح.");});
        }catch(Exception e){runOnUiThread(()->toast("تعذر التحويل: "+e.getMessage()));}}).start();
    }
    String extract(Uri u)throws Exception{
        String n=name(u).toLowerCase(Locale.ROOT);
        if(n.endsWith(".pdf"))return ocrPdf(u);
        if(n.matches(".*\\.(jpg|jpeg|png|bmp|tif|tiff|webp)$"))return ocrBitmap(BitmapFactory.decodeStream(getContentResolver().openInputStream(u)));
        if(n.endsWith(".docx")||n.endsWith(".xlsx")||n.endsWith(".pptx"))return extractZipXml(u);
        return "الملف بصيغة قديمة. استخدم DOCX/XLSX/PPTX أو PDF/صورة.";
    }
    String ocrPdf(Uri u)throws Exception{
        File f=copyTemp(u,"pdf");PdfRenderer r=new PdfRenderer(ParcelFileDescriptor.open(f,ParcelFileDescriptor.MODE_READ_ONLY));StringBuilder s=new StringBuilder();
        for(int i=0;i<r.getPageCount();i++){PdfRenderer.Page p=r.openPage(i);Bitmap b=Bitmap.createBitmap(p.getWidth()*2,p.getHeight()*2,Bitmap.Config.ARGB_8888);p.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);p.close();s.append(ocrBitmap(b)).append("\n");b.recycle();}r.close();f.delete();return s.toString();
    }
    String ocrBitmap(Bitmap b)throws Exception{
        if(b==null)throw new Exception("تعذر قراءة الصورة");
        File td=new File(getFilesDir(),"tessdata");if(!td.exists())td.mkdirs();asset("tessdata/ara.traineddata",new File(td,"ara.traineddata"));asset("tessdata/eng.traineddata",new File(td,"eng.traineddata"));
        TessBaseAPI t=new TessBaseAPI();if(!t.init(getFilesDir().getAbsolutePath(),"ara+eng"))throw new Exception("فشل تشغيل OCR");t.setImage(b);String x=t.getUTF8Text();t.recycle();return x==null?"":x;
    }
    void asset(String a,File d)throws Exception{if(d.exists()&&d.length()>1000)return;InputStream in=getAssets().open(a);FileOutputStream o=new FileOutputStream(d);byte[] b=new byte[8192];int n;while((n=in.read(b))>0)o.write(b,0,n);in.close();o.close();}
    String extractZipXml(Uri u)throws Exception{
        File f=copyTemp(u,"zip");ZipFile z=new ZipFile(f);StringBuilder s=new StringBuilder();Enumeration<? extends ZipEntry> es=z.entries();
        while(es.hasMoreElements()){ZipEntry e=es.nextElement();String n=e.getName();if(!n.endsWith(".xml")||!(n.contains("word/")||n.contains("xl/")||n.contains("ppt/")))continue;
            InputStream in=z.getInputStream(e);Document d=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);NodeList ts=d.getElementsByTagNameNS("*","t");for(int i=0;i<ts.getLength();i++)s.append(ts.item(i).getTextContent()).append(" ");s.append("\n");in.close();}
        z.close();f.delete();return s.toString();
    }
    File copyTemp(Uri u,String ext)throws Exception{File f=new File(getCacheDir(),System.currentTimeMillis()+"."+ext);InputStream in=getContentResolver().openInputStream(u);FileOutputStream o=new FileOutputStream(f);byte[] b=new byte[65536];int n;while((n=in.read(b))>0)o.write(b,0,n);in.close();o.close();return f;}
    void writeDocx(Uri out,String text)throws Exception{
        File f=new File(getCacheDir(),"converted.docx");ZipOutputStream z=new ZipOutputStream(new FileOutputStream(f));
        put(z,"[Content_Types].xml","<?xml version=\"1.0\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");
        put(z,"_rels/.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");
        put(z,"word/_rels/document.xml.rels","<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"/>");
        StringBuilder b=new StringBuilder("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
        for(String line:text.split("\\n",-1))b.append("<w:p><w:pPr><w:bidi/></w:pPr><w:r><w:t xml:space=\"preserve\">").append(xml(line)).append("</w:t></w:r></w:p>");
        b.append("<w:sectPr/></w:body></w:document>");put(z,"word/document.xml",b.toString());z.close();
        InputStream in=new FileInputStream(f);OutputStream o=getContentResolver().openOutputStream(out);byte[] buf=new byte[65536];int n;while((n=in.read(buf))>0)o.write(buf,0,n);in.close();o.close();f.delete();
    }
    void put(ZipOutputStream z,String n,String s)throws Exception{z.putNextEntry(new ZipEntry(n));z.write(s.getBytes("UTF-8"));z.closeEntry();}
    String xml(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
