package com.github.geequery.core.support;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.persistence.Entity;
import javax.persistence.EntityManagerFactory;

import com.github.geequery.accelerator.asm.ASMUtils;
import com.github.geequery.accelerator.asm.AnnotationVisitor;
import com.github.geequery.accelerator.asm.ClassReader;
import com.github.geequery.accelerator.asm.ClassVisitor;
import com.github.geequery.accelerator.asm.Opcodes;
import com.github.geequery.annotation.EasyEntity;
import com.github.geequery.core.DbClient;
import com.github.geequery.dialect.type.ColumnMapping;

import jef.database.DataObject;
import jef.database.DbCfg;
import jef.database.QB;
import jef.database.jpa.JefEntityManagerFactory;
import jef.database.meta.ITableMetadata;
import jef.database.meta.MetaHolder;
import jef.database.query.Query;

import com.github.geequery.tools.ClassScanner;
import com.github.geequery.tools.JefConfiguration;
import com.github.geequery.tools.csvreader.Codecs;
import com.github.geequery.tools.csvreader.CsvWriter;
import com.github.geequery.tools.reflect.Property;
import com.github.geequery.tools.resource.IResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.geequery.orm.annotation.InitializeData;

/**
 * 将表中的数据导出成为CSV格式。。
 * 
 * @author jiyi
 *
 */
public class InitDataExporter {
    private final Logger logger = LoggerFactory.getLogger(InitDataExporter.class);
    private DbClient session;
    private boolean deleteEmpty;
    private File rootPath = new File(System.getProperty("user.dir"));
    private String extension = "." + JefConfiguration.get(DbCfg.INIT_DATA_EXTENSION, "txt");
    private String charset = "UTF-8";

    /**
     * @param emf EntityManagerFactory
     *            数据库客户端
     */
    public InitDataExporter(EntityManagerFactory emf) {
    	if(emf instanceof JefEntityManagerFactory){
    		this.session=((JefEntityManagerFactory) emf).getDefault();
    	}else{
    		throw new UnsupportedOperationException();
    	}
    }
    /**
     * @param session
     *            数据库客户端
     */
    public InitDataExporter(DbClient session) {
        this.session = session;
    }

    /**
     * 
     * @param session
     *            数据库客户端
     * @param target
     *            生成的资源文件路径
     */
    public InitDataExporter(DbClient session, File target) {
        this.session = session;
        this.rootPath = target;
    }

    /**
     * 导出制定的实体类数据
     * 
     * @param clz
     */
    public void export(@SuppressWarnings("rawtypes") Class clz) {
        try {
            export0(clz);
        } catch (SQLException e) {
            throw new IllegalArgumentException(e);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 将数据库中的数据全部作为初始化数据导出到src/main/resources下。
     * 
     * @throws ClassNotFoundException
     * @throws IOException
     * 
     * @throws Exception
     */
    public void exportPackage(String packageName) throws ClassNotFoundException, IOException {
        ClassScanner cs = new ClassScanner();
        IResource[] entities = cs.scan(packageName);
        for (IResource clz : entities) {
            ClassReader cl = new ClassReader(clz.getInputStream(), true);
            ClassAnnotationExtracter ae = new ClassAnnotationExtracter();
            cl.accept(ae, ClassReader.SKIP_CODE);
            if (ae.hasAnnotation(Entity.class) || ae.hasAnnotation(EasyEntity.class)) {
                Class<?> e = Class.forName(cl.getJavaClassName());
                if (DataObject.class.isAssignableFrom(e)) {
                    InitializeData data = e.getAnnotation(InitializeData.class);
                    if (data != null)
                        logger.info("Starting export data:{}", e.getName());
                    export(e);
                }
            }

        }
    }

    private void export0(@SuppressWarnings("rawtypes") Class clz) throws SQLException, IOException {
        ITableMetadata meta = MetaHolder.getMeta(clz);
        if (meta == null)
            return;

        File file = new File(rootPath, meta.getThisType().getName() + extension);
        @SuppressWarnings("unchecked")
        Query<?> query = QB.create(clz);
        query.setCascade(false);
        List<?> o = session.select(query);
        if (o.isEmpty()) {
            if (deleteEmpty && file.exists()) {
                file.delete();
            }
            return;
        }

        CsvWriter cw = new CsvWriter(file, ',', charset);
        try {
            Collection<ColumnMapping> columns = meta.getColumns();
            for (ColumnMapping column : columns) {
                cw.write("[" + column.fieldName() + "]");
            }
            cw.endRecord();
            for (Object obj : o) {
                for (ColumnMapping column : columns) {
                    Property accessor = column.getFieldAccessor();
                    String data = Codecs.toString(accessor.get(obj), accessor.getGenericType());
                    cw.write(data);
                }
                cw.endRecord();
            }
        } finally {
            cw.close();
        }
        logger.info("{} was updated.", file.getAbsolutePath());
    }

    static class ClassAnnotationExtracter extends ClassVisitor {
        private Set<String> annotations = new HashSet<String>();

        public ClassAnnotationExtracter() {
            super(Opcodes.ASM5);
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            annotations.add(desc);
            return null;
        }

        public boolean hasAnnotation(Class<? extends Annotation> clzName) {
            return annotations.contains(ASMUtils.getDesc(clzName));
        }
    }

    public String getCharset() {
        return charset;
    }

    public void setCharset(String charset) {
        this.charset = charset;
    }
}
