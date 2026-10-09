<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="My checks" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<section class="wrap page">
  <div class="page-head">
    <div>
      <h1 class="h1">My checks</h1>
      <p>Every idea you have checked, newest first. Download them to attach to your proposal.</p>
    </div>
    <c:if test="${not empty rows}"><a class="btn-line" href="${ctx}/app/history.csv" download>Download CSV</a></c:if>
  </div>
  <c:choose>
    <c:when test="${empty rows}">
      <p class="empty">No checks yet. <a class="link" href="${ctx}/app/check">Check your first idea</a>.</p>
    </c:when>
    <c:otherwise>
      <div class="panel">
        <table class="data">
          <thead><tr><th>Idea</th><th class="hide-sm">Closest past project</th><th class="num">Score</th><th>Verdict</th></tr></thead>
          <tbody>
            <c:forEach var="r" items="${rows}">
              <tr>
                <td><a class="link m-title" href="${ctx}/app/result?id=${r.id}"><c:out value="${r.title}"/></a>
                  <div class="muted"><c:out value="${r.when}"/> · <c:out value="${r.domain}"/> · <c:out value="${r.type}"/><c:if test="${r.claimFlag}"> · claims flagged</c:if></div></td>
                <td class="hide-sm"><c:out value="${r.closest}"/></td>
                <td class="num"><c:out value="${r.score}"/>%</td>
                <td><span class="v-${r.verdict}"><c:out value="${r.verdict == 'DUPLICATE' ? 'Already done' : (r.verdict == 'SIMILAR' ? 'Overlaps' : (r.verdict == 'ORIGINAL' ? 'Looks original' : 'Not enough data'))}"/></span></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </c:otherwise>
  </c:choose>
</section>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
