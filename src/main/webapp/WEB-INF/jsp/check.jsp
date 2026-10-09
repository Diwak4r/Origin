<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="Check an idea" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<section class="wrap page">
  <div class="page-head">
    <div>
      <h1 class="h1">Check an idea</h1>
      <p>Describe what the project does. Origin compares it with every past project and with the ideas your classmates have locked.</p>
    </div>
  </div>

  <c:if test="${not empty startedFrom}">
    <p class="warn">Started from the open idea <strong><c:out value="${startedFrom.title}"/></strong>. Write your own description of how your group would build it.</p>
  </c:if>
  <c:if test="${not empty errors.form}"><p class="form-error" role="alert"><c:out value="${errors.form}"/></p></c:if>

  <div class="split">
    <form class="form panel" method="post" action="${ctx}/app/check" novalidate>
      <input type="hidden" name="_csrf" value="${csrf}">

      <div class="field ${not empty errors.title ? 'bad' : ''}">
        <label for="title">Title</label>
        <input id="title" name="title" maxlength="160" required value="<c:out value='${form.title}'/>" placeholder="Kalimati Price Watch for Farmers">
        <c:if test="${not empty errors.title}"><p class="err"><c:out value="${errors.title}"/></p></c:if>
      </div>

      <div class="row2">
        <div class="field ${not empty errors.domainId ? 'bad' : ''}">
          <label for="domainId">Domain</label>
          <select id="domainId" name="domainId" required>
            <option value="">Choose a domain</option>
            <c:forEach var="d" items="${domains}">
              <option value="${d.id}" ${form.domainId == d.id ? 'selected' : ''}><c:out value="${d.name}"/></option>
            </c:forEach>
          </select>
          <c:if test="${not empty errors.domainId}"><p class="err"><c:out value="${errors.domainId}"/></p></c:if>
        </div>
        <div class="field ${not empty errors.typeId ? 'bad' : ''}">
          <label for="typeId">Project type</label>
          <select id="typeId" name="typeId" required>
            <option value="">Choose a type</option>
            <c:forEach var="t" items="${types}">
              <option value="${t.id}" ${form.typeId == t.id ? 'selected' : ''}><c:out value="${t.name}"/></option>
            </c:forEach>
          </select>
          <c:if test="${not empty errors.typeId}"><p class="err"><c:out value="${errors.typeId}"/></p></c:if>
        </div>
      </div>

      <div class="field ${not empty errors.targetSemester ? 'bad' : ''}">
        <label for="targetSemester">Semester you are building this in</label>
        <select id="targetSemester" name="targetSemester" required>
          <option value="">Choose a semester</option>
          <c:forEach var="n" begin="1" end="8">
            <option value="${n}" ${form.targetSemester == n ? 'selected' : ''}>Semester ${n}</option>
          </c:forEach>
        </select>
        <c:if test="${not empty errors.targetSemester}"><p class="err"><c:out value="${errors.targetSemester}"/></p></c:if>
      </div>

      <div class="field ${not empty errors.abstractText ? 'bad' : ''}">
        <label for="abstractText">What it does</label>
        <p class="hint">Two to five sentences. Who uses it, what they do with it, and how it works.</p>
        <textarea id="abstractText" name="abstractText" data-max="2000" maxlength="2000" required><c:out value="${form.abstractText}"/></textarea>
        <c:if test="${not empty errors.abstractText}"><p class="err"><c:out value="${errors.abstractText}"/></p></c:if>
      </div>

      <div class="field ${not empty errors.problem ? 'bad' : ''}">
        <label for="problem">The problem it solves <span class="muted">(optional)</span></label>
        <p class="hint">Who suffers today and how.</p>
        <textarea id="problem" name="problem" data-max="400" maxlength="400"><c:out value="${form.problem}"/></textarea>
        <c:if test="${not empty errors.problem}"><p class="err"><c:out value="${errors.problem}"/></p></c:if>
      </div>

      <div class="field ${not empty errors.tags ? 'bad' : ''}">
        <label for="tags">Tags</label>
        <p class="hint">What the project does, separated by commas. Pick from the list or type your own.</p>
        <input id="tags" name="tags" required value="<c:out value='${form.tags}'/>" placeholder="market-price, farmer, sms" autocomplete="off">
        <c:if test="${not empty errors.tags}"><p class="err"><c:out value="${errors.tags}"/></p></c:if>
        <div class="tag-picks" aria-label="Common tags">
          <c:forEach var="t" items="${tagNames}">
            <button type="button" class="tag-pick" data-tag="<c:out value='${t}'/>"><c:out value="${t}"/></button>
          </c:forEach>
        </div>
      </div>

      <div><button class="btn btn-lg" type="submit">Check originality</button></div>
    </form>

    <aside>
      <div class="panel">
        <h2 class="h3">Past projects Origin checks against</h2>
        <ul class="plain-list">
          <c:forEach var="p" items="${corpusSample}">
            <li><div class="t"><c:out value="${p.title}"/></div><div class="s"><c:out value="${p.year}"/></div></li>
          </c:forEach>
        </ul>
      </div>
      <div class="panel">
        <h2 class="h3">How the score works</h2>
        <dl class="breakdown">
          <div><dt>Tags in common</dt><dd><span class="bar" data-w="45"></span><b>45%</b><i>of the score</i></dd></div>
          <div><dt>Words in common</dt><dd><span class="bar" data-w="35"></span><b>35%</b><i>of the score</i></dd></div>
          <div><dt>Same type</dt><dd><span class="bar" data-w="20"></span><b>20%</b><i>of the score</i></dd></div>
        </dl>
        <p class="muted spaced">Below 35% looks original. 35 to 59% overlaps. 60% and above is already done.</p>
      </div>
    </aside>
  </div>
</section>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
