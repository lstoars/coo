package coo.core.security.aspect;

import java.util.Map;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import coo.base.util.Assert;
import coo.base.util.StringUtils;
import coo.core.hibernate.dao.DaoUtils;
import coo.core.model.UuidEntity;
import coo.core.security.annotations.DetailLog;
import coo.core.security.entity.BnLogEntity;
import coo.core.util.AspectUtils;

/**
 * 详细业务日志切面。
 */
@Aspect
public class DetailLogAspect extends AbstractLogAspect {
	/**
	 * 切面处理方法。
	 * 
	 * @param joinPoint
	 *            切入点
	 * @throws Throwable
	 *             切面处理失败时抛出异常。
	 */
	@Around("@annotation(coo.core.security.annotations.DetailLog)")
	public void around(ProceedingJoinPoint joinPoint) throws Throwable {
		DetailLog log = AspectUtils.getAnnotation(joinPoint, DetailLog.class);
		Map<String, Object> params = AspectUtils.getMethodParams(joinPoint);

		Object target = getEntity(params.get(log.target()));
		Assert.notNull(target);

		BnLogEntity bnLog = newBnLog();
		bnLog.setMessage(getMessage(log.code(), log.vars(), params));

		switch (log.type()) {
		case ALL:
			processAll(bnLog, target, joinPoint);
			break;
		case ORIG:
			processOrig(bnLog, target, joinPoint);
			break;
		case NEW:
			processNew(bnLog, target, joinPoint);
			break;
		}

		saveBnLog(bnLog);
	}

	/**
	 * 记录原记录和新记录。
	 * 
	 * @param bnLog
	 *            业务日志
	 * @param target
	 *            目标对象
	 * @param joinPoint
	 *            切入点
	 * @throws Throwable
	 *             切面处理失败时抛出异常。
	 */
	private void processAll(BnLogEntity bnLog, Object target,
			ProceedingJoinPoint joinPoint) throws Throwable {
		bnLog.setEntityId(getEntityId(target));
		bnLog.setOrigData(target);
		joinPoint.proceed();
		if (target instanceof UuidEntity) {
			target = getEntity(target);
		}
		bnLog.setNewData(target);
	}

	/**
	 * 记录原记录。
	 * 
	 * @param bnLog
	 *            业务日志
	 * @param target
	 *            目标对象
	 * @param joinPoint
	 *            切入点
	 * @throws Throwable
	 *             切面处理失败时抛出异常。
	 */
	private void processOrig(BnLogEntity bnLog, Object target,
			ProceedingJoinPoint joinPoint) throws Throwable {
		bnLog.setEntityId(getEntityId(target));
		bnLog.setOrigData(target);
		joinPoint.proceed();
	}

	/**
	 * 记录新记录。
	 * 
	 * @param bnLog
	 *            业务日志
	 * @param target
	 *            目标对象
	 * @param joinPoint
	 *            切入点
	 * @throws Throwable
	 *             切面处理失败时抛出异常。
	 */
	private void processNew(BnLogEntity bnLog, Object target,
			ProceedingJoinPoint joinPoint) throws Throwable {
		joinPoint.proceed();
		bnLog.setEntityId(getEntityId(target));
		bnLog.setNewData(target);
	}

	/**
	 * 获取业务实体。
	 * 
	 * @param target
	 *            日志目标对象
	 * @return 如果目标对象是UuidEntity返回对应的业务实体，否则返回原目标对象。
	 */
	private Object getEntity(Object target) {
		if (target instanceof UuidEntity) {
			UuidEntity entity = (UuidEntity) target;
			if (StringUtils.isNotBlank(entity.getId())) {
				return DaoUtils
						.getUuidEntity(entity.getClass(), entity.getId());
			}
		}
		return target;
	}

	/**
	 * 获取业务实体ID。
	 * 
	 * @param target
	 *            日志目标对象
	 * @return 返回业务实体ID。
	 */
	private String getEntityId(Object target) {
		if (target instanceof UuidEntity) {
			return ((UuidEntity) target).getId();
		} else {
			return null;
		}
	}
}