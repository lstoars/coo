package coo.core.hibernate.dao;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.apache.lucene.search.SortField;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.ReplicationMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.CriteriaSpecification;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.Projection;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.internal.CriteriaImpl;
import org.hibernate.metadata.ClassMetadata;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.search.FullTextQuery;
import org.hibernate.search.FullTextSession;
import org.hibernate.search.Search;
import org.hibernate.search.annotations.Analyze;
import org.hibernate.search.annotations.IndexedEmbedded;
import org.hibernate.transform.ResultTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import coo.base.constants.Chars;
import coo.base.model.Page;
import coo.base.util.BeanUtils;
import coo.base.util.StringUtils;
import coo.core.hibernate.search.FullTextCriteria;

/**
 * 泛型DAO。
 * 
 * @param <T>
 *            业务实体类型
 */
public class Dao<T> {
	private final Logger log = LoggerFactory.getLogger(getClass());
	@Resource
	private SessionFactory sessionFactory;
	private Class<T> clazz;
	private Map<String, Analyze> searchFields = new LinkedHashMap<String, Analyze>();

	/**
	 * 构造方法。
	 * 
	 * @param clazz
	 *            业务实体类
	 */
	public Dao(Class<T> clazz) {
		this.clazz = clazz;
		initSearchFields();
	}

	/**
	 * 获取Hibernate的Session。
	 * 
	 * @return 返回Hibernate的Session。
	 */
	public Session getSession() {
		return sessionFactory.getCurrentSession();
	}

	/**
	 * 获取Hibernate的全文搜索Session。
	 * 
	 * @return 返回Hibernate的全文搜索Session。
	 */
	public FullTextSession getFullTextSession() {
		return Search.getFullTextSession(getSession());
	}

	/**
	 * 根据指定的ID加载业务实体。
	 * 
	 * @param id
	 *            实体ID
	 * @return 返回指定的ID加载业务实体，如果对象不存在抛出异常。
	 */
	@SuppressWarnings("unchecked")
	public T load(Serializable id) {
		return (T) getSession().load(clazz, id);
	}

	/**
	 * 根据指定的ID获取业务实体。
	 * 
	 * @param id
	 *            实体ID
	 * @return 返回指定ID的业务实体，如果没有找到则返回null。
	 */
	@SuppressWarnings("unchecked")
	public T get(Serializable id) {
		Object entity = getSession().get(clazz, id);
		// 如果获取的是一个代理对象，则从代理对象中获取实际的实体对象返回。
		if (entity instanceof HibernateProxy) {
			entity = ((HibernateProxy) entity).getHibernateLazyInitializer()
					.getImplementation();
		}
		return (T) entity;
	}

	/**
	 * 持久化业务实体。
	 * 
	 * @param entity
	 *            待持久化业务实体
	 */
	public void persist(T entity) {
		getSession().persist(entity);
	}

	/**
	 * 保存或更新业务实体。
	 * 
	 * @param entity
	 *            待保存业务实体
	 */
	public void save(T entity) {
		getSession().save(entity);
	}

	/**
	 * 更新业务实体。
	 * 
	 * @param entity
	 *            待更新业务实体
	 */
	public void update(T entity) {
		getSession().update(entity);
	}

	/**
	 * 合并业务实体。
	 * 
	 * @param entity
	 *            待更新业务实体
	 * @return 返回更新后的业务实体（持久状态的）。
	 */
	@SuppressWarnings("unchecked")
	public T merge(T entity) {
		return (T) getSession().merge(entity);
	}

	/**
	 * 保存业务实体。（复用已有的ID键值时使用）
	 * 
	 * @param entity
	 *            待保存的业务实体
	 */
	public void replicate(T entity) {
		getSession().replicate(entity, ReplicationMode.EXCEPTION);
	}

	/**
	 * 删除业务实体。
	 * 
	 * @param entity
	 *            待删除业务实体
	 */
	public void remove(T entity) {
		getSession().delete(entity);
	}

	/**
	 * 根据ID删除业务实体。
	 * 
	 * @param id
	 *            待删除业务实体ID
	 */
	public void remove(Serializable id) {
		remove(get(id));
	}

	/**
	 * 根据ID批量删除业务实体。
	 * 
	 * @param ids
	 *            待删除业务实体ID数组
	 */
	public void remove(Serializable[] ids) {
		for (Serializable id : ids) {
			remove(id);
		}
	}

	/**
	 * 删除多个业务实体。
	 * 
	 * @param entitys
	 *            待删除的业务实体列表
	 */
	public void remove(List<T> entitys) {
		for (T entity : entitys) {
			remove(entity);
		}
	}

	/**
	 * 根据属性批量删除业务实体
	 * 
	 * @param name
	 *            属性名
	 * @param value
	 *            属性值
	 */
	public void removeBy(String name, Object value) {
		Query query = createQuery("delete from " + clazz.getName() + " where "
				+ name + "=?", value);
		query.executeUpdate();
	}

	/**
	 * 清理当前Session。
	 */
	public void clear() {
		getSession().clear();
	}

	/**
	 * 创建一个绑定实体类型的查询条件。
	 * 
	 * @param criterions
	 *            查询条件
	 * @return 返回一个查询条件。
	 */
	public Criteria createCriteria(Criterion... criterions) {
		Criteria criteria = getSession().createCriteria(clazz);
		for (Criterion c : criterions) {
			criteria.add(c);
		}
		return criteria;
	}

	/**
	 * 创建一个查询对象。
	 * 
	 * @param hql
	 *            HQL语句
	 * @param values
	 *            参数值
	 * @return 返回一个查询对象。
	 */
	public Query createQuery(String hql, Object... values) {
		Query query = getSession().createQuery(hql);
		for (int i = 0; i < values.length; i++) {
			query.setParameter(i, values[i]);
		}
		return query;
	}

	/**
	 * 创建一个SQL查询对象。
	 * 
	 * @param sql
	 *            SQL语句
	 * @param values
	 *            参数值
	 * @return 返回一个SQL查询对象。
	 */
	public Query createSQLQuery(String sql, Object... values) {
		Query query = getSession().createSQLQuery(sql);
		for (int i = 0; i < values.length; i++) {
			query.setParameter(i, values[i]);
		}
		return query;
	}

	/**
	 * 创建一个绑定实体并设定了排序的查询条件。
	 * 
	 * @param orderBy
	 *            排序属性
	 * @param isAsc
	 *            是否升序
	 * @param criterions
	 *            查询条件
	 * @return 返回一个已设定排序的查询条件。
	 */
	public Criteria createCriteria(String orderBy, Boolean isAsc,
			Criterion... criterions) {
		Criteria criteria = createCriteria(criterions);
		if (isAsc) {
			criteria.addOrder(Order.asc(orderBy));
		} else {
			criteria.addOrder(Order.desc(orderBy));
		}
		return criteria;
	}

	/**
	 * 获取指定类型的所有业务实体。
	 * 
	 * @return 返回指定类型的所有业务实体。
	 */
	@SuppressWarnings("unchecked")
	public List<T> getAll() {
		Criteria criteria = createCriteria();
		return criteria.list();
	}

	/**
	 * 获取指定类型的所有业务实体并进行排序。
	 * 
	 * @param orderBy
	 *            排序的属性名
	 * @param isAsc
	 *            是否升序
	 * @return 返回排序后的指定类型的所有业务实体。
	 */
	@SuppressWarnings("unchecked")
	public List<T> getAll(String orderBy, Boolean isAsc) {
		Criteria criteria = createCriteria(orderBy, isAsc);
		return criteria.list();
	}

	/**
	 * 根据属性的值查找业务实体。
	 * 
	 * @param name
	 *            属性名
	 * @param value
	 *            属性值
	 * @return 返回属性值相符的业务实体集合，如果没有找到返回一个空的集合。
	 */
	@SuppressWarnings("unchecked")
	public List<T> findBy(String name, Object value) {
		Criteria criteria = createCriteria();
		if (value == null) {
			criteria.add(Restrictions.isNull(name));
		} else {
			criteria.add(Restrictions.eq(name, value));
		}
		return criteria.list();
	}

	/**
	 * 根据属性的值查找业务实体并进行排序。
	 * 
	 * @param name
	 *            属性名
	 * @param value
	 *            属性值
	 * @param orderBy
	 *            排序属性
	 * @param isAsc
	 *            是否升序
	 * @return 返回排序后的属性值相符的业务实体集合，如果没有找到返回一个空的集合。
	 */
	@SuppressWarnings("unchecked")
	public List<T> findBy(String name, Object value, String orderBy,
			boolean isAsc) {
		Criteria criteria = createCriteria(orderBy, isAsc);
		if (value == null) {
			criteria.add(Restrictions.isNull(name));
		} else {
			criteria.add(Restrictions.eq(name, value));
		}
		return criteria.list();
	}

	/**
	 * 判断是否存在属性重复的业务实体。
	 * 
	 * @param entity
	 *            待判断的业务实体
	 * @param propNames
	 *            属性名，可以多个属性名用","分割
	 * @return 如果存在重复的业务实体返回false，否则返回true。
	 */
	public Boolean isUnique(T entity, String propNames) {
		Criteria criteria = createCriteria().setProjection(
				Projections.rowCount());
		String[] nameList = propNames.split(Chars.COMMA);
		try {
			for (String name : nameList) {
				criteria.add(Restrictions.eq(name,
						BeanUtils.getField(entity, name)));
			}
			// 更新实体类时应该排除自身
			String idName = getIdName();
			Serializable id = getId(entity);
			if (id != null) {
				criteria.add(Restrictions.not(Restrictions.eq(idName, id)));
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return Integer.parseInt(criteria.uniqueResult().toString()) == 0;
	}

	/**
	 * 查找唯一业务实体。
	 * 
	 * @param criteria
	 *            查询条件
	 * @return 返回唯一业务实体，如果没有找到返回null。
	 */
	@SuppressWarnings("unchecked")
	public T findUnique(Criteria criteria) {
		return (T) criteria.uniqueResult();
	}

	/**
	 * 根据属性的值查找唯一的业务实体。
	 * 
	 * @param name
	 *            属性名
	 * @param value
	 *            属性值
	 * @return 返回指定唯一的业务实体，如果没有找到则返回null。
	 */
	public T findUnique(String name, Object value) {
		Criteria criteria = createCriteria(Restrictions.eq(name, value));
		return findUnique(criteria);
	}

	/**
	 * 根据HQL查询语句进行分页查询。
	 * 
	 * @param hql
	 *            HQL查询语句
	 * @param pageNo
	 *            待获取的页数
	 * @param pageSize
	 *            每页的记录数
	 * @param totalCount
	 *            总记录数
	 * @param values
	 *            参数值
	 * @return 返回查询得到的分页对象。
	 */
	@SuppressWarnings("unchecked")
	public Page<T> findPage(String hql, Integer pageNo, Integer pageSize,
			Integer totalCount, Object... values) {
		if (totalCount < 1) {
			return new Page<T>(pageSize);
		}

		Page<T> page = new Page<T>(totalCount, pageNo, pageSize);
		Query query = createQuery(hql, values);

		List<T> list = query.setFirstResult((page.getNumber() - 1) * pageSize)
				.setMaxResults(pageSize).list();
		page.setContents(list);
		return page;
	}

	/**
	 * 根据HQL查询语句进行分页查询。
	 * 
	 * @param hql
	 *            HQL查询语句
	 * @param pageNo
	 *            待获取的页数
	 * @param pageSize
	 *            每页的记录数
	 * @param values
	 *            参数值
	 * @return 返回查询得到的分页对象。
	 */
	public Page<T> findPage(String hql, Integer pageNo, Integer pageSize,
			Object... values) {
		return findPage(hql, pageNo, pageSize, count(hql, values), values);
	}

	/**
	 * 根据查询条件进行分页查询。
	 * 
	 * @param criteria
	 *            查询条件
	 * @param pageNo
	 *            待获取的页数
	 * @param pageSize
	 *            每页的记录数
	 * @param totalCount
	 *            总记录数
	 * @return 返回查询得到的分页对象。
	 */
	@SuppressWarnings("unchecked")
	public Page<T> findPage(Criteria criteria, Integer pageNo,
			Integer pageSize, Integer totalCount) {
		if (totalCount < 1) {
			return new Page<T>(pageSize);
		}

		Page<T> page = new Page<T>(totalCount, pageNo, pageSize);
		List<T> list = criteria
				.setFirstResult((page.getNumber() - 1) * pageSize)
				.setMaxResults(pageSize).list();
		page.setContents(list);
		return page;
	}

	/**
	 * 根据查询条件进行分页查询。
	 * 
	 * @param criteria
	 *            查询条件
	 * @param pageNo
	 *            待获取的页数
	 * @param pageSize
	 *            每页的记录数
	 * @return 返回查询得到的分页对象。
	 */
	public Page<T> findPage(Criteria criteria, Integer pageNo, Integer pageSize) {
		return findPage(criteria, pageNo, pageSize, count(criteria));
	}

	/**
	 * 创建全文搜索查询条件。
	 * 
	 * @return 返回全文搜索查询条件。
	 */
	public FullTextCriteria createFullTextCriteria() {
		return new FullTextCriteria(getFullTextSession(), clazz, searchFields);
	}

	/**
	 * 根据全文搜索查询条件进行全文搜索。
	 * 
	 * @param criteria
	 *            全文搜索查询条件
	 * @return 返回符合查询条件的业务实体列表。
	 */
	@SuppressWarnings("unchecked")
	public List<T> searchBy(FullTextCriteria criteria) {
		return criteria.generateQuery().list();
	}

	/**
	 * 全文搜索指定类型的所有业务实体。
	 * 
	 * @return 返回指定类型的所有业务实体。
	 */
	public List<T> searchAll() {
		return searchBy(createFullTextCriteria());
	}

	/**
	 * 全文搜索指定类型的所有业务实体并进行排序。
	 * 
	 * @param orderBy
	 *            排序的属性名
	 * @param isAsc
	 *            是否升序
	 * @param type
	 *            类型
	 * @return 返回排序后的指定类型的所有业务实体。
	 */
	public List<T> searchAll(String orderBy, Boolean isAsc, SortField.Type type) {
		FullTextCriteria criteria = createFullTextCriteria();
		if (isAsc) {
			criteria.addSortAsc(orderBy, type);
		} else {
			criteria.addSortDesc(orderBy, type);
		}
		return searchBy(criteria);
	}

	/**
	 * 全文搜索唯一业务实体。
	 * 
	 * @param criteria
	 *            全文搜索查询条件
	 * @return 返回唯一业务实体，如果没有找到返回null。
	 */
	@SuppressWarnings("unchecked")
	public T searchUnique(FullTextCriteria criteria) {
		return (T) criteria.generateQuery().uniqueResult();
	}

	/**
	 * 根据属性的值全文搜索唯一的业务实体。
	 * 
	 * @param name
	 *            属性名
	 * @param value
	 *            属性值
	 * @return 返回唯一业务实体，如果没有找到则返回null。
	 */
	public T searchUnique(String name, Object value) {
		FullTextCriteria criteria = createFullTextCriteria();
		criteria.addFilterField(name, value);
		return searchUnique(criteria);
	}

	/**
	 * 根据全文搜索查询条件进行分页全文搜索。
	 * 
	 * @param criteria
	 *            全文搜索查询条件
	 * @param pageNo
	 *            待获取的页数
	 * @param pageSize
	 *            每页的记录数
	 * @return 返回搜索得到的分页对象。
	 */
	@SuppressWarnings("unchecked")
	public Page<T> searchPage(FullTextCriteria criteria, Integer pageNo,
			Integer pageSize) {
		FullTextQuery fullTextQuery = criteria.generateQuery();
		int total = 0;
		try {
			// 当实体对应数据库中没有记录，其索引文件未生成时该方法会抛出异常
			// 这里捕捉后忽略该异常
			total = fullTextQuery.getResultSize();
		} catch (Exception e) {
			log.warn("实体[" + clazz + "]全文索引文件尚未生成。", e);
		}
		if (total < 1) {
			return new Page<T>(pageSize);
		}

		Page<T> page = new Page<T>(total, pageNo, pageSize);
		fullTextQuery.setFirstResult((page.getNumber() - 1) * pageSize)
				.setMaxResults(pageSize);
		List<T> result = fullTextQuery.list();
		page.setContents(result);
		return page;
	}

	/**
	 * 获取查询所能获得的对象总数。
	 * 
	 * @param criteria
	 *            全文搜索查询对象
	 * @return 返回查询结果总数。
	 */
	public Integer count(FullTextCriteria criteria) {
		FullTextQuery fullTextQuery = criteria.generateQuery();
		try {
			// 当实体对应数据库中没有记录，其索引文件未生成时该方法会抛出异常
			// 这里捕捉后忽略该异常
			return fullTextQuery.getResultSize();
		} catch (Exception e) {
			log.warn("实体[" + clazz + "]全文索引文件尚未生成。", e);
			return 0;
		}
	}

	/**
	 * 获取查询所能获得的对象总数。<br/>
	 * 本函数只能自动处理简单的hql语句,复杂的hql查询请另行编写count语句查询。
	 * 
	 * @param hql
	 *            查询语句
	 * @param values
	 *            查询参数
	 * @return 返回查询结果总数。
	 */
	public Integer count(String hql, Object... values) {
		String fromHql = hql;
		fromHql = "from " + StringUtils.substringAfter(fromHql, "from");
		fromHql = StringUtils.substringBefore(fromHql, "order by");
		String countHql = "select count(*) " + fromHql;
		return Integer.valueOf(createQuery(countHql, values).uniqueResult()
				.toString());
	}

	/**
	 * 获取查询所能获得的对象总数。
	 * 
	 * @param criteria
	 *            查询对象
	 * @return 返回查询结果总数。
	 */
	public Integer count(Criteria criteria) {
		CriteriaImpl impl = (CriteriaImpl) criteria;
		Projection projection = impl.getProjection();
		ResultTransformer transformer = impl.getResultTransformer();

		Field orderEntriesField = BeanUtils.findField(CriteriaImpl.class,
				"orderEntries");
		@SuppressWarnings("unchecked")
		List<CriteriaImpl.OrderEntry> orderEntries = (List<CriteriaImpl.OrderEntry>) BeanUtils
				.getField(impl, orderEntriesField);
		BeanUtils.setField(impl, orderEntriesField,
				new ArrayList<CriteriaImpl.OrderEntry>());

		int totalCount = Integer.parseInt(criteria
				.setProjection(Projections.rowCount()).uniqueResult()
				.toString());

		criteria.setProjection(projection);
		if (projection == null) {
			criteria.setResultTransformer(CriteriaSpecification.ROOT_ENTITY);
		}
		if (transformer != null) {
			criteria.setResultTransformer(transformer);
		}
		BeanUtils.setField(impl, orderEntriesField, orderEntries);

		return totalCount;
	}

	/**
	 * 执行count查询获得记录总数。
	 * 
	 * @return 返回记录总数。
	 */
	public Integer count() {
		Criteria criteria = createCriteria();
		return Integer.parseInt(criteria.setProjection(Projections.rowCount())
				.uniqueResult().toString());
	}

	/**
	 * 获取实体类的主键值。
	 * 
	 * @param entity
	 *            业务实体
	 * @return 返回实体类的主键值。
	 */
	private Serializable getId(T entity) {
		return (Serializable) BeanUtils.getField(entity, getIdName());
	}

	/**
	 * 获取实体类的主键名。
	 * 
	 * @return 返回实体类的主键名。
	 */
	private String getIdName() {
		ClassMetadata meta = sessionFactory.getClassMetadata(clazz);
		return meta.getIdentifierPropertyName();
	}

	/**
	 * 获取绑定实体类以及一级关联类的全文索引名称集合。
	 */
	private void initSearchFields() {
		// 获取实体本身声明的索引字段
		searchFields.putAll(getIndexedFields(clazz));
		// 获取实体标注为关联索引对象中声明的索引字段名
		List<Field> embeddedEntityFields = BeanUtils.findField(clazz,
				IndexedEmbedded.class);
		for (Field embeddedEntityField : embeddedEntityFields) {
			searchFields.putAll(getEmbeddedIndexedFields(embeddedEntityField));
		}
	}

	/**
	 * 获取指定类中声明为索引字段的属性名称列表。
	 * 
	 * @param clazz
	 *            类
	 * @return 返回指定类中声明为索引字段的属性名称列表。
	 */
	private Map<String, Analyze> getIndexedFields(Class<?> clazz) {
		Map<String, Analyze> indexedFields = new LinkedHashMap<String, Analyze>();
		for (Field field : BeanUtils.findField(clazz,
				org.hibernate.search.annotations.Field.class)) {
			String fieldName = field.getName();
			Analyze analyze = field.getAnnotation(
					org.hibernate.search.annotations.Field.class).analyze();
			indexedFields.put(fieldName, analyze);
		}
		return indexedFields;
	}

	/**
	 * 获取关联属性对象类中声明为索引字段的属性名称列表。
	 * 
	 * @param embeddedEntityField
	 *            关联属性
	 * @return 返回关联属性对象类中声明为索引字段的属性名称列表。
	 */
	private Map<String, Analyze> getEmbeddedIndexedFields(
			Field embeddedEntityField) {
		Class<?> embeddedClass = embeddedEntityField.getType();
		if (Collection.class.isAssignableFrom(embeddedClass)) {
			Type fc = embeddedEntityField.getGenericType();
			if (fc instanceof ParameterizedType) {
				ParameterizedType pt = (ParameterizedType) fc;
				embeddedClass = (Class<?>) pt.getActualTypeArguments()[0];
			}
		}

		String prefix = embeddedEntityField.getName() + ".";
		Map<String, Analyze> embeddedIndexedFields = new LinkedHashMap<String, Analyze>();
		Map<String, Analyze> indexedFields = getIndexedFields(embeddedClass);
		for (String fieldName : indexedFields.keySet()) {
			embeddedIndexedFields.put(prefix + fieldName,
					indexedFields.get(fieldName));
		}
		return embeddedIndexedFields;
	}
}
