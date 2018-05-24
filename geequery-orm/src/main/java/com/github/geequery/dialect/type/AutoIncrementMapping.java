package com.github.geequery.dialect.type;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import javax.persistence.GenerationType;
import javax.persistence.SequenceGenerator;
import javax.persistence.TableGenerator;

import jef.common.Entry;
import jef.database.DbMetaData;
import jef.database.DbUtils;
import jef.database.Field;
import jef.database.ORMConfig;
import jef.database.OperateTarget;
import jef.database.Sequence;
import jef.database.annotation.PartitionFunction;
import jef.database.annotation.PartitionKey;

import com.github.geequery.dialect.ColumnType;
import com.github.geequery.dialect.ColumnType.AutoIncrement;
import com.github.geequery.dialect.DatabaseDialect;
import com.github.geequery.entity.IQueryableEntity;

import jef.database.meta.Feature;
import jef.database.meta.EntityMetadata;
import jef.database.meta.MetaHolder;
import jef.database.meta.object.Column;
import jef.database.wrapper.clause.InsertSqlClause;
import jef.database.wrapper.processor.InsertStep;
import jef.database.wrapper.processor.InsertStep.JdbcAutoGeneratedKeyCallback;
import jef.database.wrapper.processor.InsertStep.SequenceGenerateCallback;
import jef.tools.StringUtils;
import jef.tools.reflect.Property;

/**
 * 自增的映射实现
 * 
 * @author jiyi
 * 
 * @param <T>
 */
public abstract class AutoIncrementMapping extends AColumnMapping {
	protected Property accessor;
	private int len;
	private boolean isBig;

	// 缓存的计算结果
	private transient GenerationResolution generationType;
	private transient String[] sequenceName;
	private transient InsertStep autoGenerateCall;

	public enum GenerationResolution {
		TABLE,

		SEQUENCE,

		IDENTITY_SKIP,

		IDENTITY_DEFAULT,

		CHECK_IS_IDENTITY
	}

	@Override
	public void init(Field field, String columnName, ColumnType type, EntityMetadata meta) {
		super.init(field, columnName, type, meta);
		len = ((AutoIncrement) type).getPrecision();
		this.isBig = len > 10;
	}

	/**
	 * 返回Sequence所在的数据源的名称（重定向已计算）
	 * 
	 * @return <li>null表示缺省数据库（表在哪里，Sequence就在哪里。）</li> <li>""表示使用默认数据源。</li>
	 */
	public String getSequenceDataSource(DatabaseDialect profile) {
		if (bindedProfile != profile || sequenceName == null) {
			String name = profile.getColumnNameToUse(this);
			rebind(DbUtils.escapeColumn(profile, name), profile);
		}
		return sequenceName[0];
	}

	@Override
	public boolean isGenerated() {
		return true;
	}

	/**
	 * 返回Sequence的名称
	 * 
	 * @return
	 */
	public String getSequenceName(DatabaseDialect profile) {
		if (bindedProfile != profile || sequenceName == null) {
			String name = profile.getColumnNameToUse(this);
			rebind(DbUtils.escapeColumn(profile, name), profile);
		}
		return sequenceName[1];
	}

	/*
	 * 计算生成策略
	 */
	@Override
	protected void rebind(String escapedColumn, DatabaseDialect profile) {
		super.rebind(escapedColumn, profile);

		AutoIncrement a = (AutoIncrement) columnDef;
		GenerationType type = a.getGenerationType(profile, this.meta.getEffectPartitionKeys() == null);// 只有非分表的类允许使用Identity方式生成，其他都仅允许Seq或Tble
		this.generationType = getResolution(type, profile);
		sequenceName = getSequenceName0(meta.getSchema(), meta.getTableName(false), type);
		autoGenerateCall = new JdbcAutoGeneratedKeyCallback(accessor, getColumnName(profile, false), profile);
	}

	private GenerationResolution getResolution(GenerationType type, DatabaseDialect profile) {
		if (type == GenerationType.IDENTITY) {
			if (profile.has(Feature.AI_TO_SEQUENCE_WITHOUT_DEFAULT)) {
				return GenerationResolution.CHECK_IS_IDENTITY;
			}
			if (profile.has(Feature.NOT_SUPPORT_KEYWORD_DEFAULT)) {
				return GenerationResolution.IDENTITY_SKIP;
			}
			return GenerationResolution.IDENTITY_DEFAULT;
		} else if (type == GenerationType.SEQUENCE) {
			return GenerationResolution.SEQUENCE;
		} else if (type == GenerationType.TABLE) {
			return GenerationResolution.TABLE;
		}
		throw new UnsupportedOperationException(type.name());
	}

	/**
	 * 
	 * @param profile
	 * @return
	 */
	public GenerationResolution getGenerationType(DatabaseDialect profile) {
		if (profile != bindedProfile || sequenceName == null) {
			String name = profile.getColumnNameToUse(this);
			rebind(DbUtils.escapeColumn(profile, name), profile);
		}
		return generationType;
	}

	private String[] getSequenceName0(String schema, String tableName, GenerationType gtype) {
		AutoIncrement type = (AutoIncrement) columnDef;
		SequenceGenerator sg = type.getSeqGenerator();
		// 多数据源下，数据源必须计算得到，不能用null表示
		boolean isMultiDatasource = isTableOnMultipleDataSources();

		if (gtype != GenerationType.TABLE) {
			if (sg != null && StringUtils.isNotEmpty(sg.sequenceName())) {
				if (StringUtils.isEmpty(schema)) {
					schema = sg.schema();
				}
				String datasource = getDataSource(sg, isMultiDatasource);
				if (StringUtils.isNotEmpty(schema)) {
					schema = MetaHolder.getMappingSchema(schema);
					return new String[] { datasource, schema + "." + sg.sequenceName() };
				} else {
					return new String[] { datasource, sg.sequenceName() };
				}
			}
		}
		if (gtype != GenerationType.SEQUENCE) {
			TableGenerator tg = type.getTableGenerator();
			if (tg != null && StringUtils.isNotEmpty(tg.table())) {
				if (StringUtils.isEmpty(schema)) {
					schema = tg.schema();
				}
				String datasource = getDataSource(tg, isMultiDatasource);
				if (StringUtils.isNotEmpty(schema)) {
					schema = MetaHolder.getMappingSchema(schema);
					return new String[] { datasource, schema + "." + tg.table() };
				} else {
					return new String[] { datasource, tg.table() };
				}
			}
		}
		// 即便不使用Seq，sequenceName也必须有值，否则会反复计算
		return new String[] { isMultiDatasource ? "" : null, DbUtils.calcSeqNameByTable(schema, tableName, this.rawColumnName) };
	}

	private boolean isTableOnMultipleDataSources() {
		boolean multiDb = false;
		if (meta.getEffectPartitionKeys() != null) {
			for (@SuppressWarnings("rawtypes")
			Entry<PartitionKey, PartitionFunction> pk : meta.getEffectPartitionKeys()) {
				if (pk.getKey().isDbName()) {
					multiDb = true;
					break;
				}
			}
		}
		return multiDb && !ORMConfig.getInstance().isSingleSite();
	}

	/**
	 * Use the field catalog to specify the name of 'datasource'. (though I know
	 * the field doesn't mean this in JPA)
	 * 
	 * @param tg
	 * @param partition
	 * @return if single site, return null. or return the name of datasource for
	 *         multiple site.
	 */
	private String getDataSource(TableGenerator tg, boolean partition) {
		if (!partition)
			return null;
		String datasource = "";
		if (tg != null) {
			datasource = MetaHolder.getMappingSite(tg.catalog());
		}
		return datasource == null ? "" : datasource;
	}

	/**
	 * Use the field catalog to specify the name of 'datasource'. (though I know
	 * the field doesn't mean this in JPA)
	 * 
	 * @param sg
	 * @param partition
	 * @return if single site, return null. or return the name of datasource for
	 *         multiple site.
	 */
	private String getDataSource(SequenceGenerator sg, boolean partition) {
		if (!partition)
			return null;
		String datasource = "";
		if (sg != null) {
			datasource = MetaHolder.getMappingSite(sg.catalog());
		}
		return datasource == null ? "" : datasource;
	}

	public void processInsert(Object value, InsertSqlClause result, List<String> cStr, List<String> vStr, boolean smart, IQueryableEntity obj) throws SQLException {
		DatabaseDialect profile = result.profile;
		Field field = this.field;

		// 手动指定
		if (isAssignedSequence(value) && ORMConfig.getInstance().isManualSequence() && obj.isUsed(field)) {
			cStr.add(rawColumnName);
			vStr.add(value.toString());
			return;
		}

		// 核对和刷新生成策略，后续操作对象许多都是从当前对象缓存结果中获取的。所以先刷新一下
		GenerationResolution type = getGenerationType(profile);
		switch (type) {
		case CHECK_IS_IDENTITY:
			boolean hasDefaultValue=checkMetadata(result);
			if(hasDefaultValue){
				this.generationType=GenerationResolution.IDENTITY_DEFAULT;
			}else{
				this.generationType=GenerationResolution.SEQUENCE;
			}
			processInsert(value,result,cStr,vStr,smart,obj);
			break;
		case IDENTITY_DEFAULT:
			cStr.add(cachedEscapeColumnName);
			vStr.add("DEFAULT");
			// 注意此处不加break(无误);
		case IDENTITY_SKIP:
			result.getCallback().addProcessor(autoGenerateCall);
			break;
		case SEQUENCE:
		case TABLE:
		default:
			String dbKey = result.getTable() == null ? null : result.getTable().getDatabase();
			OperateTarget db = new OperateTarget(result.parent, dbKey);
			Sequence seq = db.getSequence(this);
			result.getCallback().addProcessor(autoGenerateCall);
			cStr.add(cachedEscapeColumnName);
			vStr.add(seq.getName() + ".nextval");//FIXME if the resolution is TABLE...
			break;
		}
	}

	private boolean checkMetadata(InsertSqlClause result) throws SQLException {
		String dbKey = result.getTable() == null ? null : result.getTable().getDatabase();
		DbMetaData meta=result.parent.getNoTransactionSession().getMetaData(dbKey);
		Column column=meta.getColumn(this.meta.getTableName(true), this.rawColumnName);
		String val=column.getColumnDef();
		return val!=null && val.contains("nextval");
	}

	@Override
	public void processPreparedInsert(IQueryableEntity obj, List<String> cStr, List<String> vStr, InsertSqlClause result, boolean smart) throws SQLException {
		DatabaseDialect profile = result.profile;
		Field field = this.field;
		// 手动指定
		if (obj.isUsed(field) && ORMConfig.getInstance().isManualSequence() && isAssignedSequence(accessor.get(obj))) {
			cStr.add(cachedEscapeColumnName);
			vStr.add("?");
			result.addField(this);
			return;
		}
		// 核对和刷新生成策略，后续操作对象许多都是从当前对象缓存结果中获取的。所以先刷新一下
		GenerationResolution gType = getGenerationType(profile);
		// 是否需要返回自增值
		boolean returnKeys = !result.isExtreme();
		switch (gType) {
		case CHECK_IS_IDENTITY:
			boolean hasDefaultValue=checkMetadata(result);
			if(hasDefaultValue){
				this.generationType=GenerationResolution.IDENTITY_DEFAULT;
			}else{
				this.generationType=GenerationResolution.SEQUENCE;
			}
			processPreparedInsert(obj,cStr,vStr,result,smart);
			break;
		case IDENTITY_DEFAULT:
			cStr.add(cachedEscapeColumnName);
			vStr.add("DEFAULT");
			// 注意此处不加break(无误);
		case IDENTITY_SKIP:
			if (returnKeys) {
				result.getCallback().addProcessor(autoGenerateCall);
			}
			break;
		case SEQUENCE:
		case TABLE:
		default:
			String dbKey = result.getTable() == null ? null : result.getTable().getDatabase();
			OperateTarget db = new OperateTarget(result.parent, dbKey);
			Sequence sh = db.getSequence(this);
			if (!returnKeys && sh.isRawNative()) {// 可以用简略方式操作
				cStr.add(cachedEscapeColumnName);
				vStr.add(sh.getName() + ".nextval");
			} else {
				result.getCallback().addProcessor(new SequenceGenerateCallback(accessor, sh));
				cStr.add(cachedEscapeColumnName);
				vStr.add("?");
				result.addField(this);
			}
			break;
		}
	}

	/*
	 * 判断是否已经指定了自增序号的值, 如果用户已经赋了有效的值，那么就无需再自动生成
	 */
	private boolean isAssignedSequence(Object value) {
		if (value instanceof Number) {
			return ((Number) value).longValue() > 0;
		} else {
			return false;
		}
	}

	public Property getAccessor() {
		return accessor;
	}

	public int getSqlType() {
		return isBig ? Types.BIGINT : Types.INTEGER;
	}
}
