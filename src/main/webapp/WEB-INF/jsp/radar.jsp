<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Class radar" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<section class="wrap page">
  <div class="page-head">
    <div>
      <h1 class="h1">Class radar</h1>
      <p><c:out value="${semester}"/>. Every group locks one idea. When a new idea lands close to one already locked, both groups see the clash here, so it gets sorted out before anyone builds.</p>
    </div>
    <c:if test="${not empty currentUser.groupCode and empty myProposal}">
      <a class="btn" href="${ctx}/app/check">Check an idea for <c:out value="${currentUser.groupCode}"/></a>
    </c:if>
  </div>

  <c:choose>
    <c:when test="${empty proposals}"><p class="empty">No group has locked an idea this semester.</p></c:when>
    <c:otherwise>
      <div class="panel">
        <table class="data">
          <thead>
            <tr><th>Group</th><th>Idea</th><th class="hide-sm">Domain and type</th><th class="num hide-sm">Closest match</th><th>Clashes</th><th>Review</th></tr>
          </thead>
          <tbody>
            <c:forEach var="p" items="${proposals}">
              <tr class="${p.groupCode == currentUser.groupCode ? 'mine' : ''}">
                <td>
                <strong><c:out value="${p.groupCode}"/></strong>
                <c:if test="${not empty p.memberNames}">
                  <div class="muted member-names">
                    <c:forEach var="n" items="${p.memberNames}" varStatus="ms"><c:out value="${n}"/>${ms.last ? '' : '<br>'}</c:forEach>
                  </div>
                </c:if>
              </td>
                <td><div class="m-title"><c:out value="${p.title}"/></div><div class="muted"><c:out value="${p.lockedByName}"/> · <c:out value="${p.lockedText}"/></div></td>
                <td class="hide-sm"><c:out value="${p.domainName}"/><br><span class="muted"><c:out value="${p.typeName}"/></span></td>
                <td class="num hide-sm"><span class="v-${p.verdict}"><c:out value="${p.percent}"/>%</span></td>
                <td>
                  <c:choose>
                    <c:when test="${empty p.collidesWith}"><span class="muted">none</span></c:when>
                    <c:otherwise><c:forEach var="x" items="${p.collidesWith}" varStatus="s"><span class="clash"><c:out value="${x}"/></span>${s.last ? '' : '<br>'}</c:forEach></c:otherwise>
                  </c:choose>
                </td>
                <td><span class="status ${p.status}"><c:out value="${p.status == 'PENDING' ? 'Waiting for review' : (p.status == 'APPROVED' ? 'Approved' : 'Sent back by supervisor')}"/></span></td>
              </tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
    </c:otherwise>
  </c:choose>
</section>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
