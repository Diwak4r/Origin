<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Ideas" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<section class="wrap page">
  <div class="page-head">
    <div>
      <h1 class="h1">Ideas for the next project</h1>
      <p><c:out value="${problemsSeed}"/> real problems from Nepal, each built from syllabus topics. Ranked by strength: how untouched its topic mix is, how much of the syllabus it uses, how buildable in one semester and how useful. Plain CRUD ideas come last.<c:if test="${problemsStudent > 0}"> Another <c:out value="${problemsStudent}"/> problems reported by students this semester join the list when the semester closes.</c:if></p>
    </div>
  </div>

  <form class="filters" method="get" action="${ctx}/app/ideas">
    <div class="field">
      <label for="topic">Syllabus topic</label>
      <select id="topic" name="topic">
        <option value="">Any topic</option>
        <c:forEach var="tp" items="${topics}"><option value="${tp.id}" ${filter.topic == tp.id ? 'selected' : ''}><c:out value="${tp.name}"/></option></c:forEach>
      </select>
    </div>
    <div class="field">
      <label for="level">Difficulty</label>
      <select id="level" name="level">
        <option value="">Any</option>
        <option value="1" ${filter.level == 1 ? 'selected' : ''}>Easy only</option>
        <option value="2" ${filter.level == 2 ? 'selected' : ''}>Up to moderate</option>
        <option value="3" ${filter.level == 3 ? 'selected' : ''}>Up to challenging</option>
      </select>
    </div>
    <label class="check"><input type="checkbox" name="taken" value="1" ${filter.taken ? 'checked' : ''}> Show taken ideas</label>
    <button class="btn-line" type="submit">Filter</button>
  </form>

  <c:choose>
    <c:when test="${empty ideas}"><p class="empty">No ideas match these filters. Try another domain or difficulty.</p></c:when>
    <c:otherwise>
      <ol class="idea-list">
        <c:forEach var="s" items="${ideas}">
          <li class="idea ${s.open ? '' : 'taken'}">
            <div class="idea-score"><span class="big"><c:out value="${s.strengthPercent}"/></span><small>strength</small></div>
            <div class="idea-body">
              <h3><c:out value="${s.title}"/></h3>
              <c:if test="${not empty s.twist}"><p class="idea-twist"><c:out value="${s.twist}"/></p></c:if>
              <p class="idea-problem"><c:out value="${s.statement}"/></p>
              <p class="idea-proof"><c:out value="${s.proofLine}"/> <c:out value="${s.unitsLine}"/></p>
              <c:if test="${not empty s.topics}"><p class="idea-topics"><c:forEach var="tp" items="${s.topics}" varStatus="x"><c:out value="${tp}"/>${x.last ? '' : ', '}</c:forEach></p></c:if>
              <p class="idea-meta"><c:out value="${s.typeName}"/> · <c:out value="${s.domainName}"/> · for <c:out value="${s.affected}"/></p>
              <p class="near">
                <c:choose>
                  <c:when test="${empty s.nearestTitle}">Nothing close in the corpus.</c:when>
                  <c:otherwise>Closest existing work: <strong><c:out value="${s.nearestTitle}"/></strong>, <c:out value="${s.nearestPercent}"/>% alike.</c:otherwise>
                </c:choose>
                <c:choose>
                  <c:when test="${s.gapCount == 0}">No past <c:out value="${s.typeName}"/> in this domain.</c:when>
                  <c:otherwise><c:out value="${s.gapCount}"/> past <c:out value="${s.typeName}"/>${s.gapCount == 1 ? '' : 's'} in this domain.</c:otherwise>
                </c:choose>
              </p>
            </div>
            <div class="idea-side">
              <c:choose>
                <c:when test="${s.open}"><a class="btn" href="${ctx}/app/check?idea=${s.id}">Start from this</a></c:when>
                <c:otherwise><span class="status PENDING">Taken by <c:out value="${s.takenByGroup}"/></span></c:otherwise>
              </c:choose>
              <div class="meters">
                <div class="meter">New<span class="bar" data-w="${s.noveltyPercent}"></span><b><c:out value="${s.noveltyPercent}"/></b></div>
                <div class="meter">Buildable<span class="bar" data-w="${s.feasibilityPercent}"></span><b><c:out value="${s.feasibilityPercent}"/></b></div>
                <div class="meter">Useful<span class="bar" data-w="${s.impactPercent}"></span><b><c:out value="${s.impactPercent}"/></b></div>
              </div>
              <span class="muted"><c:out value="${s.difficultyLabel}"/></span>
            </div>
          </li>
        </c:forEach>
      </ol>
      <c:if test="${pages > 1}">
        <nav class="pager" aria-label="Pages">
          <span class="muted"><c:out value="${total}"/> ideas · page <c:out value="${page}"/> of <c:out value="${pages}"/></span>
          <span class="pager-links">
            <c:forEach begin="1" end="${pages}" var="n">
              <c:url var="pageUrl" value="/app/ideas">
                <c:if test="${filter.level > 0}"><c:param name="level" value="${filter.level}"/></c:if>
                <c:if test="${filter.topic > 0}"><c:param name="topic" value="${filter.topic}"/></c:if>
                <c:if test="${filter.taken}"><c:param name="taken" value="1"/></c:if>
                <c:param name="page" value="${n}"/>
              </c:url>
              <a href="${pageUrl}" class="${n == page ? 'on' : ''}" ${n == page ? 'aria-current="page"' : ''}><c:out value="${n}"/></a>
            </c:forEach>
          </span>
        </nav>
      </c:if>
    </c:otherwise>
  </c:choose>
</section>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
