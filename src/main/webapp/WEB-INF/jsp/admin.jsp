<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Supervisor desk" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<section class="wrap page">
  <div class="page-head">
    <div>
      <h1 class="h1">Supervisor desk</h1>
      <p><c:out value="${semester}"/>. Review what groups have locked, correct the engine when it is wrong, and close the semester so this batch's work guides the next one.</p>
    </div>
    <a class="btn-line" href="${ctx}/admin/report.csv" download>Semester report (CSV)</a>
  </div>

  <div class="stats">
    <div><span class="big"><c:out value="${stats.checks}"/></span><small>ideas checked</small></div>
    <div><span class="big"><c:out value="${stats.proposals}"/></span><small>locked this semester</small></div>
    <div><span class="big"><c:out value="${stats.projects}"/></span><small>projects in the corpus</small></div>
    <div><span class="big"><c:out value="${stats.studentProblems}"/></span><small>problems reported by students</small></div>
    <div><span class="big"><c:out value="${stats.ingested}"/></span><small>files read from the DB folder</small></div>
  </div>

  <div class="split spaced">
    <div>
      <div class="panel">
        <h2 class="h2">Waiting for review</h2>
        <c:choose>
          <c:when test="${empty pending}"><p class="empty">Nothing waiting.</p></c:when>
          <c:otherwise>
            <div class="review">
              <c:forEach var="p" items="${pending}">
                <div class="review-item">
                  <div>
                    <p class="m-title"><strong><c:out value="${p.groupCode}"/></strong> · <a class="link" href="${ctx}/app/result?id=${p.checkId}"><c:out value="${p.title}"/></a></p>
                    <p class="muted"><c:out value="${p.domainName}"/> · <c:out value="${p.typeName}"/> · closest match <span class="v-${p.verdict}"><c:out value="${p.percent}"/>%</span></p>
                    <p><c:out value="${p.abstractText}"/></p>
                    <c:if test="${not empty p.collidesWith}"><p class="clash">Clashes with <c:forEach var="x" items="${p.collidesWith}" varStatus="s"><c:out value="${x}"/>${s.last ? '' : ', '}</c:forEach></p></c:if>
                  </div>
                  <form method="post" action="${ctx}/admin/review">
                    <input type="hidden" name="_csrf" value="${csrf}"><input type="hidden" name="proposalId" value="${p.id}">
                    <label class="sr-only" for="note-${p.id}">Note to the group</label>
                    <input type="text" id="note-${p.id}" name="note" maxlength="255" placeholder="Note to the group (optional)">
                    <div class="review-actions">
                      <button class="btn" type="submit" name="decision" value="APPROVED">Approve</button>
                      <button class="btn-line" type="submit" name="decision" value="REJECTED">Send back</button>
                    </div>
                  </form>
                </div>
              </c:forEach>
            </div>
          </c:otherwise>
        </c:choose>
      </div>

      <div class="panel">
        <h2 class="h2">Reviewed</h2>
        <c:choose>
          <c:when test="${empty reviewed}"><p class="empty">No decisions yet.</p></c:when>
          <c:otherwise>
            <table class="data">
              <thead><tr><th>Group</th><th>Idea</th><th>Decision</th></tr></thead>
              <tbody>
                <c:forEach var="p" items="${reviewed}">
                  <tr><td><c:out value="${p.groupCode}"/></td><td><c:out value="${p.title}"/></td><td><span class="status ${p.status}"><c:out value="${p.status == 'APPROVED' ? 'Approved' : 'Sent back'}"/></span></td></tr>
                </c:forEach>
              </tbody>
            </table>
          </c:otherwise>
        </c:choose>
      </div>

      <div class="panel">
        <h2 class="h2">Close the semester</h2>
        <p>Approved ideas join the corpus as past projects, the class radar starts empty for the new semester, and the idea list is re-scored against the bigger corpus. This runs as one database transaction: all of it happens, or none of it.</p>
        <form class="form" method="post" action="${ctx}/admin/archive">
          <input type="hidden" name="_csrf" value="${csrf}">
          <div class="row2">
            <div class="field"><label for="code">New semester code</label><input id="code" name="code" placeholder="2027-SPRING" required></div>
            <div class="field"><label for="label">Label</label><input id="label" name="label" placeholder="Spring 2027 (BIT 4th Semester)" required></div>
          </div>
          <label class="check"><input type="checkbox" name="confirm" value="yes"> I have reviewed every locked idea and want to close <c:out value="${semester}"/>.</label>
          <div><button class="btn btn-danger" type="submit">Close semester</button></div>
        </form>
      </div>
    </div>

    <aside>
      <div class="panel">
        <h2 class="h3">DB folder</h2>
        <p class="muted">Drop past projects here as CSV files. Origin checks each file about every ten seconds.</p>
        <p class="folder-path"><c:out value="${dbFolder}"/></p>
        <c:choose>
          <c:when test="${empty ingest}"><p class="empty">No file has been read yet.</p></c:when>
          <c:otherwise>
            <ul class="plain-list">
              <c:forEach var="e" items="${ingest}">
                <li>
                  <div class="t"><c:out value="${e.fileName}"/> <span class="status ${e.status}"><c:out value="${e.status == 'DONE' ? 'Read' : e.status == 'FAILED' ? 'Rejected' : 'Unsupported'}"/></span></div>
                  <div class="s"><c:out value="${e.note}"/></div>
                </li>
              </c:forEach>
            </ul>
          </c:otherwise>
        </c:choose>
      </div>
      <div class="panel">
        <h2 class="h3">Where past projects cluster</h2>
        <table class="data">
          <thead><tr><th>Domain</th><th class="num">Projects</th><th class="num">Share</th><th class="num">Latest</th></tr></thead>
          <tbody>
            <c:forEach var="s" items="${saturation}">
              <tr><td><c:out value="${s.domain}"/></td><td class="num"><c:out value="${s.projects}"/></td><td class="num"><c:out value="${s.share}"/>%</td><td class="num"><c:out value="${s.lastYear}"/></td></tr>
            </c:forEach>
          </tbody>
        </table>
      </div>
      <div class="panel">
        <h2 class="h3">What students are checking</h2>
        <c:choose>
          <c:when test="${empty trends}"><p class="empty">No checks yet.</p></c:when>
          <c:otherwise>
            <table class="data">
              <thead><tr><th>Tag</th><th class="num">Checks</th><th class="num">Past projects</th></tr></thead>
              <tbody>
                <c:forEach var="t" items="${trends}">
                  <tr><td><c:out value="${t.tag}"/></td><td class="num"><c:out value="${t.checks}"/></td><td class="num"><c:out value="${t.corpus}"/></td></tr>
                </c:forEach>
              </tbody>
            </table>
          </c:otherwise>
        </c:choose>
      </div>
      <div class="panel">
        <h2 class="h3">Audit trail</h2>
        <p class="muted">Written by database triggers, not by the application.</p>
        <ul class="plain-list">
          <c:forEach var="a" items="${audit}">
            <li><div class="t"><c:out value="${a.action}"/> <span class="muted">· <c:out value="${a.when}"/> · <c:out value="${a.who}"/></span></div><div class="s"><c:out value="${a.detail}"/></div></li>
          </c:forEach>
        </ul>
      </div>
    </aside>
  </div>
</section>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
