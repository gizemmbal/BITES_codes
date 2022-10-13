package com.xperexpo.organizationservice.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import com.xperexpo.organizationservice.client.AuthExternalService;
import com.xperexpo.organizationservice.enums.PermissionViewType;
import com.xperexpo.organizationservice.payload.PermissionListPayload;

@Component
public class UserPermissionUtils {

	@Autowired
	private RedissonClient redissonClient;

	@Autowired
	private RedisCacheNameUtils redisCacheName;

	@Lazy
	@Autowired
	private AuthExternalService authExternalService;

	public List<String> getUserPermission(String userId, Long typeId, PermissionViewType permissionViewType) {

		List<String> userPermissionByRedis = getUserPermissionByRedis(userId, typeId, permissionViewType);

		if (userPermissionByRedis == null || userPermissionByRedis.isEmpty()) {

			return addUserPermissionRedis(userId, typeId, permissionViewType);
		} else {
			return userPermissionByRedis;
		}
	}

	private List<String> addUserPermissionRedis(String userId, Long typeId, PermissionViewType permissionViewType) {
		try {

			PermissionListPayload permissionListPayload = new PermissionListPayload();
			permissionListPayload.setUserId(userId);
			if (typeId != null) {
				if (PermissionViewType.ORGANIZATION.equals(permissionViewType)) {
					permissionListPayload.setPermissionViewType(PermissionViewType.ORGANIZATION.getCode());

				} else {
					permissionListPayload.setPermissionViewType(PermissionViewType.EVENT.getCode());

				}
				permissionListPayload.setPermissionTypeId(typeId);
			} else {
				permissionListPayload.setPermissionViewType(PermissionViewType.GENERAL.getCode());

			}

			List<String> userPermissionByRedis = authExternalService.getUserPermission(permissionListPayload).getBody();
			if (userPermissionByRedis != null && !userPermissionByRedis.isEmpty()) {
				RList<String> list = redissonClient
						.getList(redisCacheName.getUserPermissionCacheName(userId, typeId, permissionViewType));
				list.expire(12, TimeUnit.HOURS);
				list.addAll(userPermissionByRedis);
			}
			return userPermissionByRedis;
		} catch (Exception e) {
			LogUtil.logError(UserPermissionUtils.class, "UserPermissionUtils Exception userId: " + userId, e);
			return new ArrayList<>();
		}
	}

	private List<String> getUserPermissionByRedis(String userId, Long organizationId,
			PermissionViewType permissionViewType) {
		RList<String> permissionRedisList = redissonClient
				.getList(redisCacheName.getUserPermissionCacheName(userId, organizationId, permissionViewType));
		return permissionRedisList.readAll();
	}

}
