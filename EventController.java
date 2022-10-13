package com.xperexpo.organizationservice.controller;

import java.util.List;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.xperexpo.organizationservice.enums.StatusCode;
import com.xperexpo.organizationservice.enums.UpdateEventStatus;
import com.xperexpo.organizationservice.exception.ExpoRuntimeException;
import com.xperexpo.organizationservice.payload.EventDTO;
import com.xperexpo.organizationservice.payload.EventQueryRequest;
import com.xperexpo.organizationservice.payload.EventSaveOrUpdateRequest;
import com.xperexpo.organizationservice.payload.SingleEventDTO;
import com.xperexpo.organizationservice.payload.UpdateStatusEvent;
import com.xperexpo.organizationservice.payload.base.BaseResponse;
import com.xperexpo.organizationservice.service.EventService;
import com.xperexpo.organizationservice.service.impl.EventServiceImpl;
import com.xperexpo.organizationservice.utils.LogUtil;
import com.xperexpo.organizationservice.utils.TenancyUtil;

@RestController
@CrossOrigin
@RequestMapping("/events")
public class EventController {

	@Autowired
	private EventService eventService;

	@Autowired
	private TenancyUtil tenancyUtil;

	@GetMapping
	public ResponseEntity<BaseResponse> getEvent() {
		String userId = TenancyUtil.findUserIdForToken();

		SingleEventDTO updateEventDTO = eventService.getEvent(tenancyUtil.getEventId(), tenancyUtil.getOrganizationId(),
				userId);

		return new ResponseEntity<>(BaseResponse.success(updateEventDTO), HttpStatus.OK);
	}

	@PostMapping
	@PreAuthorize("hasAuthority('add_event')")
	public ResponseEntity<BaseResponse> createEvent(@RequestHeader("i18nextLng") String i18n,
			@RequestPart(value = "inbag") @Valid EventSaveOrUpdateRequest request,
			@RequestPart(value = "picture") MultipartFile picture) {
		LogUtil.logInfo(EventController.class, "createEvent i18nextLng: " + i18n);

		eventService.createEvent(request, tenancyUtil.getOrganizationId(), picture);

		return new ResponseEntity<>(BaseResponse.success(), HttpStatus.OK);
	}

	@GetMapping("/url-check")
	public ResponseEntity<BaseResponse> urlCheck(@RequestParam(value = "url") String url,
			@RequestParam(value = "eventId", required = false) Long eventId) {

		BaseResponse baseResponse = BaseResponse.success();
		baseResponse.setData(eventService.urlCheck(url, eventId));
		return new ResponseEntity<>(baseResponse, HttpStatus.OK);

	}

	@PostMapping("/all")
	@PreAuthorize("hasAuthority('list_event')")
	public ResponseEntity<BaseResponse> getEvents(@RequestHeader("i18nextLng") String i18n,
			@RequestBody @Valid EventQueryRequest request) {
		LogUtil.logInfo(EventController.class, "getEvents i18nextLng: " + i18n);

		String userId = TenancyUtil.findUserIdForToken();

		List<EventDTO> events = eventService.queryEvents(i18n, request, userId, tenancyUtil.getOrganizationId());
        BaseResponse baseResponse = new BaseResponse();
        baseResponse.setSuccess(true);
        baseResponse.setStatusCode(StatusCode.OK);
        baseResponse.setMessage(StatusCode.OK.name());
        baseResponse.setData(events);
        return new ResponseEntity<>(baseResponse, HttpStatus.OK);
    }

	@PutMapping
	@PreAuthorize("hasAuthority('edit_event')")
	public ResponseEntity<BaseResponse> updateEvent(@RequestHeader("i18nextLng") String i18n,
			@RequestPart(value = "inbag") @Valid EventSaveOrUpdateRequest request,
			@RequestPart(value = "picture", required = false) MultipartFile picture) {
		String userId = TenancyUtil.findUserIdForToken();

		eventService.updateEvent(tenancyUtil.getEventId(), request, picture, userId, i18n);

		return new ResponseEntity<>(BaseResponse.success(), HttpStatus.OK);
	}

	@PutMapping("/status")
	@PreAuthorize("hasAuthority('publish-status_event')")
	public ResponseEntity<BaseResponse> statusEvent(@RequestBody @Valid UpdateStatusEvent updateStatusEvent) {
		String userId = TenancyUtil.findUserIdForToken();

		UpdateEventStatus status = UpdateEventStatus.valueOf(updateStatusEvent.getUpdateStatus());
		LogUtil.logInfo(EventServiceImpl.class, "12");

		if (status == null) {
			throw new ExpoRuntimeException("XE_31");

		}
		LogUtil.logInfo(EventServiceImpl.class, "13");

		if (UpdateEventStatus.RELEASED.equals(status)) {
			eventService.publishEvent(tenancyUtil.getEventId(), userId);
		}

		else if (UpdateEventStatus.UNPUBLISHED.equals(status)) {
			eventService.unPublishEvent(tenancyUtil.getEventId(), userId);
		}
		else {
			throw new ExpoRuntimeException("XE_31", "event status not valid for this action " + status.getCode());
		}

		return new ResponseEntity<>(BaseResponse.success(), HttpStatus.OK);
	}

	@DeleteMapping
	@PreAuthorize("hasAuthority('delete_event')")
	public ResponseEntity<BaseResponse> deleteEvent() {
		String userId = TenancyUtil.findUserIdForToken();
		eventService.deleteEvent(tenancyUtil.getEventId(), userId);

		return new ResponseEntity<>(BaseResponse.success(), HttpStatus.OK);
	}

	@GetMapping("/organization/{id}")
	public ResponseEntity<BaseResponse> findByOrganizationIdForEvent(@PathVariable Long id) {

		BaseResponse response = BaseResponse.success(eventService.findActiveEventById(id).getOrganization().getId());
		return new ResponseEntity<>(response, HttpStatus.OK);
	}

}
