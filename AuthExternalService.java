package com.xperexpo.organizationservice.client;

import java.util.List;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.xperexpo.organizationservice.payload.BaseRecordDefinition.UserRoleFindPayload;
import com.xperexpo.organizationservice.payload.DeleteUserRoleRelation;
import com.xperexpo.organizationservice.payload.EventPermissionPayload;
import com.xperexpo.organizationservice.payload.PermissionListPayload;
import com.xperexpo.organizationservice.payload.RoleRelationPayload;
import com.xperexpo.organizationservice.payload.UserActiveRolePayload;
import com.xperexpo.organizationservice.payload.UserIdAndPermissionList;
import com.xperexpo.organizationservice.payload.UserRoleParameterPayload;
import com.xperexpo.organizationservice.payload.UserRolePayload;
import com.xperexpo.organizationservice.payload.base.BaseResponse;

@FeignClient(name = "auth-service")
public interface AuthExternalService {

	@PostMapping(value = "user/role/permission/list/real", consumes = { MediaType.APPLICATION_JSON_VALUE,
			MediaType.APPLICATION_XML_VALUE }, produces = { MediaType.APPLICATION_JSON_VALUE,
					MediaType.APPLICATION_XML_VALUE })
	ResponseEntity<List<String>> getUserPermission(@RequestBody PermissionListPayload permissionListPayload);

	@PostMapping("/role")
	ResponseEntity<BaseResponse> saveRole(@RequestBody RoleRelationPayload roleRelationPayload);

	@PostMapping("/user/role")
	ResponseEntity<BaseResponse> saveUserRole(@RequestBody UserRolePayload userRolePayload);

	@PostMapping("/user/role/parameters")
	ResponseEntity<BaseResponse> saveUserRoleParameters(@RequestBody UserRoleParameterPayload userRoleParameterPayload);

	@PostMapping("/user/role/remove")
	ResponseEntity<BaseResponse> removeUserRole(@RequestBody DeleteUserRoleRelation deleteUserRoleRelation);

	@PostMapping("/role/active-role-dd")
	ResponseEntity<BaseResponse> findActiveRolesByIdsForDropDown(@RequestBody UserRoleFindPayload payload);

	@PostMapping("/user/role/rollback")
	ResponseEntity<BaseResponse> rollbackUserRolesIfExist(@RequestBody UserRoleParameterPayload payload);

	@PostMapping("/user/role/event-permission")
	List<UserIdAndPermissionList> findListPermissionEventRole(@RequestBody EventPermissionPayload payload);

	@PostMapping("/user/role/user-active-role")
	ResponseEntity<BaseResponse> getUserActiveRoles(UserActiveRolePayload payload);
}
