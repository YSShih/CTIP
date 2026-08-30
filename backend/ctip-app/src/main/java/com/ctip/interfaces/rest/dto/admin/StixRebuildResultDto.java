package com.ctip.interfaces.rest.dto.admin;

/** STIX 重投影的結果({@code POST /api/v1/admin/stix/rebuild})。 */
public record StixRebuildResultDto(int indicatorsRebuilt) {}
