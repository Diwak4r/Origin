<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %><%@ taglib prefix="fn" uri="jakarta.tags.functions" %><!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title><c:out value="${empty pageTitle ? 'Origin' : pageTitle.concat(' · Origin')}"/></title>
  <link rel="preload" href="${pageContext.request.contextPath}/static/fonts/Poppins-400.woff2" as="font" type="font/woff2" crossorigin>
  <link rel="preload" href="${pageContext.request.contextPath}/static/fonts/Poppins-600.woff2" as="font" type="font/woff2" crossorigin>
  <link rel="stylesheet" href="${pageContext.request.contextPath}/static/css/origin.css">
  <link rel="icon" href="${pageContext.request.contextPath}/static/img/mark.svg" type="image/svg+xml">
  <script src="${pageContext.request.contextPath}/static/js/origin.js" defer></script>
</head>
<body class="${bodyClass}">
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<%-- Error pages are dispatched without the security filter, so read the user straight from the session. --%>
<c:if test="${empty currentUser and not empty sessionScope.user}"><c:set var="currentUser" value="${sessionScope.user}" scope="request"/></c:if>
<c:if test="${empty csrf and not empty sessionScope.csrf}"><c:set var="csrf" value="${sessionScope.csrf}" scope="request"/></c:if>
<header class="masthead">
  <div class="masthead-inner">
    <a class="brand" href="${ctx}/" aria-label="Origin home">
      <svg class="brand-mark" viewBox="0 0 32 32" aria-hidden="true">
        <rect x="3" y="7" width="22" height="22" rx="1.5"/>
        <path d="M3 18h22M14 7v22"/>
        <path class="stake" d="M25 7 L29 3"/>
        <circle class="stake" cx="29" cy="3" r="1.6"/>
      </svg>
      <span>Origin</span>
    </a>
    <nav class="nav" aria-label="Main">
      <c:if test="${not empty currentUser}">
        <a href="${ctx}/app/check" class="${fn:startsWith(path, '/app/check') or fn:startsWith(path, '/app/result') ? 'on' : ''}">Check an idea</a>
        <a href="${ctx}/app/ideas" class="${fn:startsWith(path, '/app/ideas') ? 'on' : ''}">Ideas</a>
        <a href="${ctx}/app/radar" class="${fn:startsWith(path, '/app/radar') ? 'on' : ''}">Class radar</a>
        <a href="${ctx}/app/history" class="${fn:startsWith(path, '/app/history') ? 'on' : ''}">My checks</a>
        <c:if test="${currentUser.supervisor}">
          <a href="${ctx}/admin" class="${fn:startsWith(path, '/admin') ? 'on' : ''}">Supervisor desk</a>
        </c:if>
      </c:if>
    </nav>
    <div class="who">
      <c:choose>
        <c:when test="${not empty currentUser}">
          <span class="who-name"><c:out value="${currentUser.firstName}"/><c:if test="${not empty currentUser.groupCode}"><em><c:out value="${currentUser.groupCode}"/></em></c:if></span>
          <form method="post" action="${ctx}/logout">
            <input type="hidden" name="_csrf" value="${csrf}">
            <button class="btn-quiet" type="submit">Sign out</button>
          </form>
        </c:when>
        <c:otherwise>
          <a class="btn-quiet" href="${ctx}/login">Sign in</a>
          <a class="btn" href="${ctx}/register">Create account</a>
        </c:otherwise>
      </c:choose>
    </div>
  </div>
</header>
<c:if test="${not empty flash}">
  <div class="flash" role="status"><div class="wrap"><c:out value="${flash}"/></div></div>
</c:if>
<main id="main">
