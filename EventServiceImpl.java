package com.xperexpo.organizationservice.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.transaction.Transactional;

import org.apache.commons.io.FilenameUtils;
import org.hibernate.cache.spi.support.AbstractReadWriteAccess.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xperexpo.organizationservice.client.AuthExternalService;
import com.xperexpo.organizationservice.client.FileExternalService;
import com.xperexpo.organizationservice.converter.EventConverter;
import com.xperexpo.organizationservice.entity.Event;
import com.xperexpo.organizationservice.entity.EventIntervalDate;
import com.xperexpo.organizationservice.entity.EventTag;
import com.xperexpo.organizationservice.entity.EventTenant;
import com.xperexpo.organizationservice.entity.query.RoleType;
import com.xperexpo.organizationservice.enums.BucketFolderName;
import com.xperexpo.organizationservice.enums.EventColumn;
import com.xperexpo.organizationservice.enums.EventLocationType;
import com.xperexpo.organizationservice.enums.EventStatus;
import com.xperexpo.organizationservice.enums.EventTimeStatus;
import com.xperexpo.organizationservice.exception.ExpoRuntimeException;
import com.xperexpo.organizationservice.payload.ActiveEventEditPayload;
import com.xperexpo.organizationservice.payload.ChangePublishEventMailPayload;
import com.xperexpo.organizationservice.payload.EventDTO;
import com.xperexpo.organizationservice.payload.EventPermissionPayload;
import com.xperexpo.organizationservice.payload.EventQueryRequest;
import com.xperexpo.organizationservice.payload.EventSaveOrUpdateRequest;
import com.xperexpo.organizationservice.payload.FileNameResponse;
import com.xperexpo.organizationservice.payload.SingleEventDTO;
import com.xperexpo.organizationservice.payload.UserBaseFieldResponse;
import com.xperexpo.organizationservice.payload.UserIdAndPermissionList;
import com.xperexpo.organizationservice.repository.EventIntervalDateRepository;
import com.xperexpo.organizationservice.repository.EventRepository;
import com.xperexpo.organizationservice.repository.EventTagRepository;
import com.xperexpo.organizationservice.repository.EventTenantRepository;
import com.xperexpo.organizationservice.service.AttendeeGroupService;
import com.xperexpo.organizationservice.service.AuthService;
import com.xperexpo.organizationservice.service.BoothGroupService;
import com.xperexpo.organizationservice.service.EventService;
import com.xperexpo.organizationservice.service.MailSendService;
import com.xperexpo.organizationservice.service.OrganizationService;
import com.xperexpo.organizationservice.service.OrganizationTeamMemberService;
import com.xperexpo.organizationservice.service.SessionService;
import com.xperexpo.organizationservice.service.SponsorService;
import com.xperexpo.organizationservice.service.UserService;
import com.xperexpo.organizationservice.utils.LogUtil;
import com.xperexpo.organizationservice.utils.RegexpUtils;
import com.xperexpo.organizationservice.utils.SystemParameterUtils;
import com.xperexpo.organizationservice.utils.TimeUtils;
import com.xperexpo.organizationservice.utils.TimeZoneRedisUtils;
import com.xperexpo.organizationservice.utils.UserLanguageUtils;
import com.xperexpo.organizationservice.utils.Utils;

@Service
public class EventServiceImpl implements EventService {

	private static final String GENERAL_START_DATE = "generalStartDate";
	private static final String GENERAL_END_DATE = "generalEndDate";
	private static final String ORGANIZATION = "organization";
	private static final String EVENT_INTERNAL_DATE = "eventIntervalDate";
	private static final Locale TURKISH = Locale.forLanguageTag("tr");
	private static final String IS_ACTIVE = "isActive";

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private EventTagRepository eventTagRepository;

	@Autowired
	private EventTenantRepository eventTenantRepository;

	@Autowired
	private EventIntervalDateRepository eventIntervalDateRepository;

	@Autowired
	private FileExternalService fileExternalService;

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private EventConverter eventConverter;

	@Autowired
	private MailSendService mailSendService;

	@Autowired
	private UserService userService;

	@Autowired
	private BoothGroupService boothGroupService;

	@Autowired
	private SponsorService sponsorService;

	@Autowired
	private TimeZoneRedisUtils timeZoneRedisUtils;

	@Autowired
	private AuthService authService;

	@Autowired
	private UserLanguageUtils userLanguageUtils;

	@Autowired
	private AuthExternalService authExternalService;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private AttendeeGroupService attendeeGroupService;

	@Autowired
	private OrganizationTeamMemberService organizationTeamMemberService;

	@Transactional
	@Override
	public void createEvent(EventSaveOrUpdateRequest request, Long organizationId, MultipartFile picture) {

		eventPictureCheck(picture);

		urlCheck(request.getUrl(), null);

		Event event = new Event();
		eventConverter.toEntity(request, event, false);
		event.setEventStatus(EventStatus.DRAFT);
		event.setOrganization(organizationService.findOrganization(organizationId));
		event.setPicture(uploadLogo(picture));
		event.setEventTenant(createEventTenant());

		Event savedEvent = eventRepository.save(event);

		List<EventTag> tags = new ArrayList<>();
		for (String t : request.getTags()) {
			EventTag tag = new EventTag();
			tag.setTag(t);
			tag.setEvent(event);
			tags.add(tag);
		}

		eventTagRepository.saveAll(tags);
		sponsorService.fillDefaultSponsorGroups(event);
		sessionService.fillDefaultSessionGroups(event);
		attendeeGroupService.fillDefaultAttendeeGroups(event);

		boothGroupService.createDefaultBoothGroup(event);
		List<String> roleParameterList = List.of(SystemParameterUtils.EVENT_MANAGER_ID,
				SystemParameterUtils.BOOTH_ADMIN_ID, SystemParameterUtils.BOOTH_TEAM_MEMBER_ID);
		authService.saveDefaultRoles(RoleType.EVENT, roleParameterList, savedEvent.getId());
		LogUtil.logInfo(EventServiceImpl.class, "event successfully saved " + event.getId());
	}

	private EventTenant createEventTenant() {
		EventTenant eventTenant = new EventTenant();
		eventTenant.setActive(false);
		return eventTenantRepository.save(eventTenant);

	}

	private EventTenant updateStatusEventTenant(EventTenant eventTenant, boolean status) {
		eventTenant.setActive(status);
		return eventTenantRepository.save(eventTenant);

	}

	@Transactional
	@Override
	public SingleEventDTO getEvent(Long eventId, Long organizationId, String userId) {
		boolean isTeamMember = organizationTeamMemberService.findActiveEventForTeamMember(userId, organizationId);

		Event event = null;

		if (!isTeamMember)
		{
			event = getOwnEvent(eventId, userId);
		}
		else {
			event = eventRepository.findById(eventId).get();
		}
		
		return eventConverter.toSingleDTO(event);

	}

	@Transactional
	@Override
	public void updateEvent(Long eventId, EventSaveOrUpdateRequest request, MultipartFile picture, String userId,
			String i18n) {

		Event event = getOwnEvent(eventId, userId);

		Event updateEvent = controlEventStatusAndProcessUpdateEvent(request, event);

		if (picture != null)
			eventPictureCheck(picture);

		urlCheck(request.getUrl(), eventId);

		// picture
		if (picture != null) {
			fileExternalService.removeImage(BucketFolderName.EVENT_PICTURE.getCode(), updateEvent.getPicture());
			updateEvent.setPicture(uploadLogo(picture));
		}
		eventRepository.save(updateEvent);

		updateTags(eventId, request.getTags(), updateEvent);

		if (EventStatus.RELEASED.equals(event.getEventStatus())) {
			ActiveEventEditPayload activeEventEditPayload = new ActiveEventEditPayload();
			activeEventEditPayload.setLanguage(userLanguageUtils.getLanguageCode(userId));
			activeEventEditPayload.setEmail(event.getOrganization().getEmail());
			mailSendService.activeEventEdit(activeEventEditPayload);
		}

		LogUtil.logInfo(EventServiceImpl.class, "event successfully updated " + eventId);

	}

	private Event controlEventStatusAndProcessUpdateEvent(EventSaveOrUpdateRequest request, Event event) {
		EventTimeStatus eventTimeStatus = eventConverter.getEventTimeStatus(event);

		if (Boolean.TRUE.equals(request.getIsKnownDate()) && !Utils.isNullOrEmpty(request.getGeneralStartDate())
				&& !Utils.isNullOrEmpty(request.getGeneralEndDate())
				&& !Utils.isNullOrEmpty(request.getActiveStartDate())
				&& !Utils.isNullOrEmpty(request.getActiveEndDate())) {
			LocalDateTime activeStartDate = TimeUtils.parseDateTime(request.getActiveStartDate(), event.getTimezone());
			LocalDateTime generalStartDate = TimeUtils.parseDateTime(request.getGeneralStartDate(),
					event.getTimezone());
			LocalDateTime generalEndDate = TimeUtils.parseDateTime(request.getGeneralEndDate(), event.getTimezone());
			LocalDateTime activeEndDate = TimeUtils.parseDateTime(request.getActiveEndDate(), event.getTimezone());
			if (generalStartDate.isAfter(generalEndDate) || activeStartDate.isAfter(activeEndDate)) {
				throw new ExpoRuntimeException("XE_32");
			}
		}

		if (EventStatus.CANCELLED.equals(event.getEventStatus())) {
			throw new ExpoRuntimeException("XE_31");
		}

		if (EventTimeStatus.COMPLETED.equals(eventTimeStatus)) {
			throw new ExpoRuntimeException("XE_32");
		}

		else if (EventTimeStatus.ONGOING.equals(eventTimeStatus)) {
			Optional<EventIntervalDate> eventInternalDate = eventIntervalDateRepository
					.findById(event.getEventIntervalDate().getId());
			if (!eventInternalDate.isPresent()) {
				throw new ExpoRuntimeException("XE_16");
			}

			LocalDateTime activeStartDate = TimeUtils.parseDateTime(request.getActiveStartDate(), event.getTimezone());
			if (activeStartDate.isAfter(LocalDateTime.now())) {
				eventInternalDate.get().setActiveStartDate(TimeUtils.parseDateTime(request.getActiveStartDate(),
						request.getTimezone()));
			}

			eventInternalDate.get()
					.setActiveEndDate(TimeUtils.parseDateTime(request.getActiveEndDate(), event.getTimezone()));
			eventInternalDate.get()
					.setGeneralEndDate(TimeUtils.parseDateTime(request.getGeneralEndDate(), event.getTimezone()));

			saveEventIntervalDate(eventInternalDate.get());
		}

		else {
			eventConverter.toEntity(request, event, true);

		}

		return event;
	}

	@Override
	public EventIntervalDate findEventIntervalDate(Long id) {
		return eventIntervalDateRepository.findById(id).orElse(null);
	}

	@Override
	public List<EventTag> getEventTags(Long eventId) {
		return eventTagRepository.findAllByEventId(eventId);
	}

	private void updateTags(Long eventId, List<String> newTags, Event event) {
		// update tags
		List<EventTag> oldTags = eventTagRepository.findAllByEventId(eventId);
		List<String> oldTagsString = oldTags.stream().map(EventTag::getTag).toList();
		for (EventTag et : oldTags) {
			if (!newTags.contains(et.getTag())) {
				// sil
				eventTagRepository.delete(et);
			}
		}
		for (String t : newTags) {
			if (!oldTagsString.contains(t)) {
				// ekle
				EventTag tag = new EventTag();
				tag.setTag(t);
				tag.setEvent(event);
				eventTagRepository.save(tag);
			}
		}
	}

	@Transactional
	@Override
	public void deleteEvent(Long id, String userId) {
		Event event = getOwnEvent(id, userId);
		EventTimeStatus eventTimeStatus = eventConverter.getEventTimeStatus(event);

		if (EventStatus.RELEASED.equals(event.getEventStatus())) {
			if (EventTimeStatus.ONGOING.equals(eventTimeStatus) || EventTimeStatus.UPCOMING.equals(eventTimeStatus)) {
				throw new ExpoRuntimeException("XE_31");
			}
		}

		fileExternalService.removeImage(BucketFolderName.EVENT_PICTURE.getCode(), event.getPicture());

//		eventTagRepository.deleteAllByEventId(id);
//		eventRepository.delete(event);

		event.setActive(false);
		String deleteUUID = Utils.generateUUIDForDeleteAction();
		event.setUrl(event.getUrl() + deleteUUID);
		eventRepository.save(event);

		LogUtil.logInfo(EventServiceImpl.class, "event successfully deleted " + id);
	}

	private Event getOwnEvent(Long id, String userId) {
		Optional<Event> optEvent = eventRepository.findByIdAndIsActive(id, true);
		if (optEvent.isEmpty()) {
			throw new ExpoRuntimeException("XE_24");
		}
		Event event = optEvent.get();

		if (!userId.equals(event.getOrganization().getOrganizationOwner().getUserId())) {
			throw new ExpoRuntimeException("XE_30");
		}
		return event;
	}

	@Override
	public EventIntervalDate saveEventIntervalDate(EventIntervalDate eventIntervalDate) {
		return eventIntervalDateRepository.save(eventIntervalDate);
	}

	@Transactional
	@Override
	public void publishEvent(Long id, String userId) {

		Event event = getOwnEvent(id, userId);

		if (!event.isActive()) {
			throw new ExpoRuntimeException("XE_24");
		}

		if (!(EventStatus.DRAFT.equals(event.getEventStatus())
				|| EventStatus.UNPUBLISHED.equals(event.getEventStatus()))) {
			throw new ExpoRuntimeException("XE_31");
		}

		EventTimeStatus eventTimeStatus = eventConverter.getEventTimeStatus(event);

		if (eventTimeStatus == null || EventTimeStatus.COMPLETED.equals(eventTimeStatus)) {
			throw new ExpoRuntimeException("XE_32");
		}

		event.setEventStatus(EventStatus.RELEASED);
		updateStatusEventTenant(event.getEventTenant(), true);

		eventRepository.save(event);

		mailSendService.changePublicStatusEvent(getChangePublishEventMailPayload(event, true, userId));

		sponsorService.sendMailsWhenPublished(event);

		LogUtil.logInfo(EventServiceImpl.class, "event successfully publish " + id);
	}

	@Transactional
	@Override
	public void unPublishEvent(Long id, String userId) {
		Event event = getOwnEvent(id, userId);

		if (!event.isActive()) {
			throw new ExpoRuntimeException("XE_24");
		}

		if (EventStatus.DRAFT.equals(event.getEventStatus())
				|| EventStatus.UNPUBLISHED.equals(event.getEventStatus())) {
			throw new ExpoRuntimeException("XE_31");
		}

		EventTimeStatus eventTimeStatus = eventConverter.getEventTimeStatus(event);

		if (EventTimeStatus.COMPLETED.equals(eventTimeStatus)) {
			throw new ExpoRuntimeException("XE_32");
		}

		event.setEventStatus(EventStatus.UNPUBLISHED);
		updateStatusEventTenant(event.getEventTenant(), false);

		eventRepository.save(event);

		mailSendService.changePublicStatusEvent(getChangePublishEventMailPayload(event, false, userId));

		LogUtil.logInfo(EventServiceImpl.class, "event successfully publish " + id);
	}

	private ChangePublishEventMailPayload getChangePublishEventMailPayload(Event event, boolean isPublish,
			String publishUserId) {
		String userId = event.getOrganization().getOrganizationOwner().getUserId();
		UserBaseFieldResponse findOrganizerUserInfo = userService.findOrganizerUserInfo(userId);
		String nameAndSurname = findOrganizerUserInfo.getName() + " " + findOrganizerUserInfo.getLastName();

		String languageCode = userLanguageUtils.getLanguageCode(publishUserId);

		String eventLocationType = EventLocationType.getValueByLang(event.getEventLocationType().getCode(),
				languageCode.toUpperCase());

		String mainNameLanguage = "";
		if (!Utils.isNullOrEmpty(event.getNameSecondLang())) {
			mainNameLanguage = languageCode.equalsIgnoreCase(event.getMainLanguage()) ? event.getNameMainLang()
					: event.getNameSecondLang();
		} else {
			mainNameLanguage = event.getNameMainLang();
		}
		String timeZoneForRedis = timeZoneRedisUtils.getTimeZoneForRedis(publishUserId);
		return ChangePublishEventMailPayload.builder().eventName(mainNameLanguage).fuarName(event.getNameMainLang())
				.fuarType(eventLocationType).organizationName(nameAndSurname).email(event.getOrganization().getEmail())
				.organizationStartDate(
						TimeUtils.getDateTimeZone(event.getEventIntervalDate().getGeneralStartDate(), timeZoneForRedis))
				.organizationEndDate(
						TimeUtils.getDateTimeZone(event.getEventIntervalDate().getGeneralEndDate(), timeZoneForRedis))
				.language(languageCode).isPublish(isPublish).build();
	}

	@Override
	public Event findActiveEventById(Long eventId) {
		Optional<Event> optionalEvent = eventRepository.findByIdAndIsActive(eventId, true);
		return optionalEvent.orElseThrow(() -> new ExpoRuntimeException("XE_24"));
	}

	@Override
	public Event findActiveEventByEventIdAndOrganizationId(Long eventId, Long organizationId) {
		return eventRepository.findByIdAndOrganizationIdAndIsActive(eventId, organizationId, false).orElse(null);
	}

	void eventPictureCheck(MultipartFile picture) {
		String extension = FilenameUtils.getExtension(picture.getOriginalFilename());
		if (extension == null)
			extension = "";

		extension = extension.toLowerCase();
		if (!(extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg"))) {
			throw new ExpoRuntimeException("XE_2", "invalid file extension");
		}

		long bytes = picture.getSize();
		if (Utils.getMegaByteSize(bytes) > 5.0) {
			throw new ExpoRuntimeException("XE_3", "file size exceeded");
		}
	}

	@Override
	public boolean urlCheck(String url, Long eventId) {

		if (url.length() > 63 || url.length() < 1) {
			throw new ExpoRuntimeException("XE_4", "wrong domain name");
		}

		// sadece sayılar ve harfler
		boolean regexControl = Pattern.matches(RegexpUtils.URL_REGEXP, url);
		if (!regexControl) {
			throw new ExpoRuntimeException("XE_4", "wrong domain name");
		}

		if (eventId == null) {
			if (eventRepository.existsByUrl(url)) {
				throw new ExpoRuntimeException("XE_5", "domain name is unavailable");
			}
		} else {
			if (eventRepository.existsByUrlAndIdNot(url, eventId)) {
				throw new ExpoRuntimeException("XE_5", "domain name is unavailable");
			}
		}
		return true;

	}

	@Override
	public long countEventForOrganizationId(Long organizationId) {
		return eventRepository.countByOrganizationIdAndIsActive(organizationId, true);
	}

	@Override
	public List<Event> findEventsForOrganizationId(Long organizationId) {
		return eventRepository.findByOrganizationIdAndIsActive(organizationId, true);
	}

	private String uploadLogo(MultipartFile logo) {
		ResponseEntity<FileNameResponse> uploadImage = fileExternalService.uploadImage(logo,
				BucketFolderName.EVENT_PICTURE.getCode());

		if (!uploadImage.getStatusCode().equals(HttpStatus.CREATED)) {
			throw new ExpoRuntimeException("XE_16", " file-service uploadImage error");
		}
		return Objects.requireNonNull(uploadImage.getBody()).getFileName();
	}

	@Override
	public long countEvents() {
		return eventRepository.count();
	}

	@Override
	public List<EventDTO> queryEvents(String i18n, EventQueryRequest request, String userId, Long organizationId) {

		// boilerplate
		CriteriaBuilder builder = entityManager.getCriteriaBuilder();
		CriteriaQuery<Event> query = builder.createQuery(Event.class);
		Root<Event> root = query.from(Event.class);

		// prepare predicates
		List<Predicate> predicates = new ArrayList<>();

		Predicate isActive = builder.equal(root.get(IS_ACTIVE), true);
		predicates.add(isActive);

		Join<Order, Item> organizationJoin = root.join(ORGANIZATION, JoinType.LEFT);
		Predicate organizationIdPredicate = builder.equal(organizationJoin.get("id"), organizationId);
		predicates.add(organizationIdPredicate);

		if (request.getSearch().length() > 2) {
			setSearchParam(request, builder, root, predicates);
		}

		if (EventColumn.TIME == request.getOrderColumn()) {
			Join<Order, Item> eventIntervalDateJoin = root.join(EVENT_INTERNAL_DATE, JoinType.LEFT);

			setOrderParamsTime(request, builder, eventIntervalDateJoin, query);

		} else {
			setOrderParams(request, builder, root, query);

		}
		List<Predicate> timePredicates = prepareTimeParams(request, builder, root);

		// Combine predicates
		Predicate searchPredicates = builder.and(predicates.toArray(Predicate[]::new));
		Predicate timesPredicates = builder.or(timePredicates.toArray(Predicate[]::new));

		Predicate finalPredicate;
		if (request.getStatusList().isEmpty()) {
			finalPredicate = builder.and(searchPredicates);
		} else {
			finalPredicate = builder.and(timesPredicates, searchPredicates);
		}

		// query and return
		query.select(root).where(finalPredicate);

		String timeZoneUser = timeZoneRedisUtils.getTimeZoneForRedis(userId);

		List<EventDTO> eventDtoList = entityManager.createQuery(query).getResultStream()
				.map(e -> eventConverter.toDto(e, timeZoneUser)).toList();

		addPermissionEventList(eventDtoList, userId);

		return eventDtoList;

	}

	private void addPermissionEventList(List<EventDTO> eventDtoList, String userId) {
		if (eventDtoList == null || eventDtoList.isEmpty()) {
			return;
		}

		EventPermissionPayload eventPermissionPayload = new EventPermissionPayload();
		List<Long> eventIdList = eventDtoList.stream().map(EventDTO::getId).toList();

		eventPermissionPayload.setOrganizationId(eventDtoList.get(0).getOrganizationId());
		eventPermissionPayload.setUserId(userId);
		eventPermissionPayload.setEventIdList(eventIdList);
		List<UserIdAndPermissionList> userIdAndPermissionList = authExternalService
				.findListPermissionEventRole(eventPermissionPayload);
		assert !userIdAndPermissionList.isEmpty();

		for (EventDTO eventDto : eventDtoList) {
			List<UserIdAndPermissionList> userEventPermissionList = userIdAndPermissionList.stream()
					.filter(permission -> permission.getEventId().equals(eventDto.getId())
							|| permission.getEventId().equals(eventDto.getOrganizationId()))
					.toList();
			eventDto.setPermissionList(
					userEventPermissionList.stream().map(UserIdAndPermissionList::getUserPermissionList).toList());
		}

	}

	private void setOrderParams(EventQueryRequest request, CriteriaBuilder builder, Root<Event> root,
			CriteriaQuery<Event> query) {
		if (request.getDirection().equals("DESC")) {
			query.orderBy(builder.desc(root.get(request.getOrderColumn().getValue())));
		} else {
			query.orderBy(builder.asc(root.get(request.getOrderColumn().getValue())));
		}
	}

	private void setOrderParamsTime(EventQueryRequest request, CriteriaBuilder builder, Join<Order, Item> root,
			CriteriaQuery<Event> query) {
		if (request.getDirection().equals("DESC")) {
			query.orderBy(builder.desc(root.get(request.getOrderColumn().getValue())));
		} else {
			query.orderBy(builder.asc(root.get(request.getOrderColumn().getValue())));
		}
	}

	private void setSearchParam(EventQueryRequest request, CriteriaBuilder builder, Root<Event> root,
			List<Predicate> predicates) {

		String searchText1 = request.getSearch().toLowerCase(TURKISH).replace("ı", "i");
		String searchText2 = request.getSearch().toLowerCase(TURKISH).replace("i", "ı");
		Predicate search1 = builder.like(builder.lower(root.get(EventColumn.NAME.getValue())), "%" + searchText1 + "%");
		Predicate search2 = builder.like(builder.lower(root.get(EventColumn.NAME.getValue())), "%" + searchText2 + "%");
		predicates.add(builder.or(search1, search2));
	}

	private List<Predicate> prepareTimeParams(EventQueryRequest request, CriteriaBuilder builder, Root<Event> root) {
		LocalDateTime now = LocalDateTime.now();
		List<Predicate> timePredicates = new ArrayList<>();
		Join<Order, Item> eventIntervalDateJoin = root.join(EVENT_INTERNAL_DATE, JoinType.LEFT);
		for (EventTimeStatus status : request.getStatusList()) {
			if (EventTimeStatus.UPCOMING.equals(status)) {
				Predicate upcoming = builder.greaterThan(eventIntervalDateJoin.get(GENERAL_START_DATE), now);
				timePredicates.add(builder.or(upcoming));
			}
			if (EventTimeStatus.ONGOING.equals(status)) {
				Predicate ongoing = builder.and(builder.lessThan(eventIntervalDateJoin.get(GENERAL_START_DATE), now),
						builder.greaterThan(eventIntervalDateJoin.get(GENERAL_END_DATE), now));
				timePredicates.add(builder.or(ongoing));
			}
			if (EventTimeStatus.COMPLETED.equals(status)) {
				Predicate past = builder.lessThan(eventIntervalDateJoin.get(GENERAL_END_DATE), now);
				timePredicates.add(builder.or(past));
			}
		}
		return timePredicates;
	}
}
