<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Weighing your idea" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<section class="wrap evaluating" data-next="${ctx}/app/result?id=${check.id}">
  <div>
    <h1 class="h1">Weighing your idea</h1>
    <p class="eval-title"><c:out value="${check.title}"/></p>
    <p class="lead">Origin reads it against every past project and every idea locked this semester.</p>
    <span class="bar" id="eval-bar"></span>
    <p class="skip"><a class="link" href="${ctx}/app/result?id=${check.id}">Show the result now</a></p>
  </div>
  <ol class="stages" id="stages">
    <li class="stage"><span class="stage-mark"></span><span class="stage-label">Reading your idea</span>
      <span class="stage-count"><c:out value="${ev.terms}"/> terms found</span></li>
    <li class="stage"><span class="stage-mark"></span><span class="stage-label">Searching the past</span>
      <span class="stage-count"><c:out value="${ev.projects}"/> projects, <c:out value="${ev.candidates}"/> share a term</span></li>
    <li class="stage"><span class="stage-mark"></span><span class="stage-label">Weighing rare words</span>
      <span class="stage-count">rare terms count more</span></li>
    <li class="stage"><span class="stage-mark"></span><span class="stage-label">Comparing with this semester</span>
      <span class="stage-count"><c:out value="${ev.classIdeas}"/> locked ideas</span></li>
    <li class="stage"><span class="stage-mark"></span><span class="stage-label">Checking coverage</span>
      <span class="stage-count"><c:out value="${ev.coveragePercent}"/>% covered by <c:out value="${ev.coverageProjects}"/> <c:out value="${ev.coverageProjects == 1 ? 'project' : 'projects'}"/></span></li>
  </ol>
  <noscript><meta http-equiv="refresh" content="3;url=${ctx}/app/result?id=${check.id}"></noscript>
</section>
<script src="${ctx}/static/js/evaluating.js" defer></script>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
