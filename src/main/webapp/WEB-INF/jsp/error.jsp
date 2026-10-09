<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" isErrorPage="true" %><%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="${empty errorTitle ? 'Something went wrong' : errorTitle}" scope="request"/>
<jsp:include page="/WEB-INF/jsp/_top.jsp"/>
<section class="wrap error-page">
  <h1 class="h1"><c:out value="${empty errorTitle ? 'Something went wrong' : errorTitle}"/></h1>
  <p><c:out value="${empty errorText ? 'Origin hit an error it did not expect. The details are in the server log. Go back and try again.' : errorText}"/></p>
  <p><a class="link" href="${pageContext.request.contextPath}/">Back to the start</a></p>
</section>
<jsp:include page="/WEB-INF/jsp/_bottom.jsp"/>
