<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${check.title}" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<c:set var="closest" value="${check.closest}"/>
<c:set var="cst" value="${empty closest ? null : extras.stories[closest.id]}"/>
<section class="wrap page">
  <p class="muted"><c:out value="${check.domainName}"/> · <c:out value="${check.typeName}"/> · for semester <c:out value="${check.targetSemester}"/> · checked <c:out value="${check.createdText}"/></p>
  <h1 class="h1"><c:out value="${check.title}"/></h1>
  <ul class="tags spaced"><c:forEach var="t" items="${check.tags}"><li><c:out value="${t}"/></li></c:forEach></ul>

  <c:if test="${check.pending}"><p class="refining" data-poll="${ctx}/app/result/status?id=${check.id}">Reading the closest match more closely. The score may shift a little.</p></c:if>

  <div class="decision spaced">
    <div class="decision-main">
      <c:choose>
        <c:when test="${not empty closest}">
          <p class="decision-kicker">Closest match</p>
          <p class="decision-line"><strong><c:out value="${closest.title}"/></strong> <span class="muted"><c:out value="${closest.origin}"/> · <c:out value="${closest.percent}"/>%</span></p>
          <c:if test="${not empty cst}">
            <c:if test="${not empty cst.shared}">
              <p class="decision-row"><b>Same as yours</b></p>
              <ul class="tags"><c:forEach var="t" items="${cst.shared}" end="3"><li class="shared"><c:out value="${t}"/></li></c:forEach></ul>
            </c:if>
            <c:if test="${not empty cst.yoursOnly}">
              <p class="decision-row"><b>Only in yours</b></p>
              <ul class="tags"><c:forEach var="t" items="${cst.yoursOnly}" end="3"><li><c:out value="${t}"/></li></c:forEach></ul>
            </c:if>
          </c:if>
        </c:when>
        <c:otherwise>
          <p class="decision-kicker">Closest match</p>
          <p class="decision-line"><strong>Nothing close enough.</strong> <span class="muted">No past project and no locked idea overlaps with this idea.</span></p>
        </c:otherwise>
      </c:choose>
    </div>

    <div class="decision-side">
      <div class="verdict ${check.verdict}">
        <div class="pct"><c:out value="${check.percent}"/>%</div>
        <div>
          <p class="label"><c:out value="${check.verdictLabel}"/></p>
          <p class="advice"><c:out value="${check.verdictAdvice}"/></p>
        </div>
      </div>
      <c:choose>
        <c:when test="${check.verdict == 'DUPLICATE'}">
          <p class="decision-action"><strong>Do not build this as it is.</strong> Change the problem, the users, or the main function, then check again.</p>
          <a class="btn" href="${ctx}/app/check?from=${check.id}">Revise this idea</a>
        </c:when>
        <c:when test="${check.verdict == 'SIMILAR'}">
          <p class="decision-action"><strong>You can build it, with a sharper core.</strong>
            <c:choose>
              <c:when test="${not empty cst and not empty cst.yoursOnly}">Build it around <c:forEach var="t" items="${cst.yoursOnly}" end="1" varStatus="x"><c:out value="${t}"/>${x.last ? '' : ' and '}</c:forEach>.</c:when>
              <c:otherwise>Change who it is for, or how it works.</c:otherwise>
            </c:choose>
          </p>
          <c:choose>
            <c:when test="${check.locked}"><p class="muted">Locked for your group.</p><a class="btn" href="${ctx}/app/radar">View Class Radar</a></c:when>
            <c:when test="${empty currentUser.groupCode}"><p class="muted">Your account has no group code. Ask your supervisor for one.</p></c:when>
            <c:when test="${not empty groupLocked}"><p class="muted">Group <c:out value="${currentUser.groupCode}"/> already locked an idea this semester.</p></c:when>
            <c:otherwise><form method="post" action="${ctx}/app/lock"><input type="hidden" name="_csrf" value="${csrf}"><input type="hidden" name="checkId" value="${check.id}"><button class="btn" type="submit">Lock for group <c:out value="${currentUser.groupCode}"/></button></form></c:otherwise>
          </c:choose>
        </c:when>
        <c:when test="${check.verdict == 'LOW_CONFIDENCE'}">
          <p class="decision-action"><strong>Too few past projects to judge.</strong> Treat the score as a hint and ask your supervisor.</p>
          <a class="btn" href="${ctx}/app/radar">View Class Radar</a>
        </c:when>
        <c:otherwise>
          <p class="decision-action"><strong>You are clear to build.</strong> Nothing close enough to block this idea.</p>
          <c:choose>
            <c:when test="${check.locked}"><p class="muted">Locked for your group.</p><a class="btn" href="${ctx}/app/radar">View Class Radar</a></c:when>
            <c:when test="${empty currentUser.groupCode}"><p class="muted">Your account has no group code. Ask your supervisor for one.</p></c:when>
            <c:when test="${not empty groupLocked}"><p class="muted">Group <c:out value="${currentUser.groupCode}"/> already locked an idea this semester.</p></c:when>
            <c:otherwise><form method="post" action="${ctx}/app/lock"><input type="hidden" name="_csrf" value="${csrf}"><input type="hidden" name="checkId" value="${check.id}"><button class="btn" type="submit">Lock for group <c:out value="${currentUser.groupCode}"/></button></form></c:otherwise>
          </c:choose>
        </c:otherwise>
      </c:choose>
      <c:if test="${check.verdict != 'DUPLICATE' and not check.locked}"><p class="spaced"><a class="link" href="${ctx}/app/check?from=${check.id}">Edit and check again</a></p></c:if>
    </div>
  </div>

  <c:if test="${not empty check.claimReasons}">
    <div class="warn spaced" role="note">
      <p><strong>Claims without substance.</strong></p>
      <c:forEach var="r" items="${check.claimReasons}"><p><c:out value="${r}"/></p></c:forEach>
    </div>
  </c:if>

  <div class="panel spaced">
    <h2 class="h3">Past projects, closest first</h2>
    <c:choose>
      <c:when test="${empty check.history}"><p class="empty">No past projects are available.</p></c:when>
      <c:otherwise>
        <table class="matches">
          <thead><tr><th>Project</th><th class="num hide-sm">Tags</th><th class="num hide-sm">Words</th><th class="num hide-sm">Type</th><c:if test="${check.meaningShown}"><th class="num hide-sm">Meaning</th></c:if><th class="num">Score</th></tr></thead>
          <tbody>
            <c:forEach var="m" items="${check.history}">
              <tr class="${m.band}">
                <td>
                  <div class="m-title"><c:out value="${m.title}"/></div>
                  <div class="m-sub"><c:out value="${m.origin}"/> · <c:out value="${m.typeName}"/></div>
                  <c:set var="st" value="${extras.stories[m.id]}"/>
                  <c:if test="${not empty st and not empty st.shared}">
                    <div class="story"><span><b>Same</b><c:forEach var="t" items="${st.shared}" end="2" varStatus="x"><c:out value="${t}"/>${x.last ? '' : ', '}</c:forEach></span></div>
                  </c:if>
                  <c:if test="${currentUser.supervisor}">
                    <div class="override">
                      <form method="post" action="${ctx}/admin/override"><input type="hidden" name="_csrf" value="${csrf}"><input type="hidden" name="checkId" value="${check.id}"><input type="hidden" name="matchId" value="${m.id}"><input type="hidden" name="verdict" value="same"><button class="btn-line" type="submit">Same project</button></form>
                      <form method="post" action="${ctx}/admin/override"><input type="hidden" name="_csrf" value="${csrf}"><input type="hidden" name="checkId" value="${check.id}"><input type="hidden" name="matchId" value="${m.id}"><input type="hidden" name="verdict" value="different"><button class="btn-line" type="submit">Different</button></form>
                    </div>
                  </c:if>
                </td>
                <td class="num hide-sm"><c:out value="${m.tagPercent}"/>%</td><td class="num hide-sm"><c:out value="${m.keywordPercent}"/>%</td><td class="num hide-sm">${m.sameType ? 'same' : 'no'}</td>
                <c:if test="${check.meaningShown}"><td class="num hide-sm"><c:choose><c:when test="${m.conceptKnown}"><c:out value="${m.conceptPercent}"/>%</c:when><c:when test="${check.pending}"><span class="pending-dash">...</span></c:when><c:otherwise><span class="pending-dash">-</span></c:otherwise></c:choose></td></c:if>
                <td class="num"><span class="final"><c:out value="${m.percent}"/>%</span></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </c:otherwise>
    </c:choose>
  </div>

  <div class="panel">
    <h2 class="h3">This semester's locked ideas</h2>
    <c:choose>
      <c:when test="${empty check.classMatches}"><p class="empty">No other group has locked an idea yet.</p></c:when>
      <c:otherwise>
        <table class="matches"><thead><tr><th>Locked idea</th><th class="num hide-sm">Tags</th><th class="num hide-sm">Words</th><c:if test="${check.meaningShown}"><th class="num hide-sm">Meaning</th></c:if><th class="num">Score</th></tr></thead>
          <tbody><c:forEach var="m" items="${check.classMatches}"><tr class="${m.band}"><td><div class="m-title"><c:out value="${m.title}"/></div><div class="m-sub"><c:out value="${m.origin}"/> · <c:out value="${m.typeName}"/></div></td><td class="num hide-sm"><c:out value="${m.tagPercent}"/>%</td><td class="num hide-sm"><c:out value="${m.keywordPercent}"/>%</td><c:if test="${check.meaningShown}"><td class="num hide-sm"><c:choose><c:when test="${m.conceptKnown}"><c:out value="${m.conceptPercent}"/>%</c:when><c:when test="${check.pending}"><span class="pending-dash">...</span></c:when><c:otherwise><span class="pending-dash">-</span></c:otherwise></c:choose></td></c:if><td class="num"><span class="final"><c:out value="${m.percent}"/>%</span></td></tr></c:forEach></tbody>
        </table>
      </c:otherwise>
    </c:choose>
  </div>
</section>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
