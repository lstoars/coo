package coo.core.util;

import org.hibernate.SessionFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import coo.core.model.UuidEntity;

/**
 * Spring工具类。
 */
@Component
@Lazy(value = false)
public class SpringUtils implements ApplicationContextAware {
	private static ApplicationContext context;

	@Override
	public void setApplicationContext(ApplicationContext context) {
		SpringUtils.context = context;
	}

	/**
	 * 获取Spring容器的应用上下文。
	 * 
	 * @return 返回Spring容器的应用上下文。
	 */
	public static ApplicationContext getContext() {
		return context;
	}

	/**
	 * 从Spring容器中获取指定名称的Bean。
	 * 
	 * @param <T>
	 *            Bean类型
	 * @param beanName
	 *            bean名称
	 * @return 返回指定名称的Bean。
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getBean(String beanName) {
		return (T) context.getBean(beanName);
	}

	/**
	 * 获取UuidEntity对象。
	 * 
	 * @param entityClass
	 *            实体类
	 * @param id
	 *            实体ID
	 * @return 返回对应的UuidEntity对象。
	 */
	@SuppressWarnings("unchecked")
	public static <T extends UuidEntity> T getUuidEntityObject(
			Class<T> entityClass, String id) {
		SessionFactory sessionFactory = getBean("sessionFactory");
		return (T) sessionFactory.getCurrentSession().get(entityClass, id);
	}
}
