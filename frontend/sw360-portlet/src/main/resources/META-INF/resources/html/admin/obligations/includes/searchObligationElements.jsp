<%@ page import="com.liferay.portal.kernel.portlet.PortletURLFactoryUtil" %>
<%@include file="/html/init.jsp" %>
<portlet:defineObjects/>
<liferay-theme:defineObjects/>

<%@ page import="org.eclipse.sw360.portal.common.PortalConstants" %>
<jsp:useBean id="obligationElement" class="org.eclipse.sw360.datahandler.thrift.licenses.ObligationElement" scope="request" />

<portlet:resourceURL var="viewObligationELementURL">
    <portlet:param name="<%=PortalConstants.ACTION%>" value="<%=PortalConstants.VIEW_IMPORT_OBLIGATION_ELEMENTS%>"/>
    <portlet:param name="<%=PortalConstants.OBLIGATION_ELEMENT_ID%>" value="${obligationElement.id}"/>
</portlet:resourceURL>
<%@ include file="/html/utils/includes/requirejs.jspf" %>
<div class="dialogs">
	<div id="searchObligationElementsDialog" data-title="Import Obligation Element" class="modal fade" tabindex="-1" role="dialog">
		<div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable" role="document">
		    <div class="modal-content">
			<div class="modal-body container">

                    <form>
                        <div class="row form-group">
                            <div class="col">
                                <input type="text" name="searchobligationelement" id="searchobligationelement" placeholder="<liferay-ui:message key="enter.search.text" />" class="form-control"/>
                            </div>
                            <div class="col">
                                <button type="button" class="btn btn-secondary" id="searchbuttonobligation"><liferay-ui:message key="search" /></button>
                            </div>
                        </div>

                        <div id="ObligationElementsearchresults">
                            <div class="spinner text-center" style="display: none;">
                                <div class="spinner-border" role="status">
                                    <span class="sr-only"><liferay-ui:message key="loading" /></span>
                                </div>
                            </div>

                            <table id="obligationElementSearchResultstable" class="table table-bordered">
                                <thead>
                                    <tr>
                                        <th></th>
                                        <th>Language Element</th>
                                        <th>Action</th>
                                        <th>Object</th>
                                    </tr>
                                </thead>
                                <tbody>
                                </tbody>
                            </table>
                        </div>
                    </form>
				</div>
			    <div class="modal-footer">
		        <button type="button" class="btn btn-light" data-dismiss="modal"><liferay-ui:message key="close" /></button>
			        <button id="importObligationElementsButton" type="button" class="btn btn-primary" title="Import Obligation Element">Import Obligation Element</button>
			    </div>
			</div>
		</div>
	</div>
</div>

<script>
    require(['jquery', 'modules/dialog', 'bridges/datatables', 'utils/keyboard', /* jquery-plugins,  'jquery-ui',*/ 'utils/link', 'utils/includes/clipboard'], function($, dialog, datatables, keyboard, link, clipboard) {
       var $dataTable,
            $dialog;
        keyboard.bindkeyPressToClick('searchobligationelement', 'searchbuttonobligation');

        $('[data-dismiss=modal]').on('click', function (e) {
            var $t = $(this),
            target = $t[0].href || $t.data("target") || $t.parents('.modal') || [];

        $(target)
           .find("input,textarea,select")
               .val('')
               .end()
           .find("input[type=checkbox], input[type=radio]")
               .prop("checked", "")
               .end();
        })

        // $('#addLinkedProjectButton').on('click', showProjectDialog);
        // $('#linkToProjectButton').on('click', showProjectDialogForLinkToProj);
        $('#searchbuttonobligation').on('click', function() {
            showObligationElementDialog()
            obligationElementContentFromAjax('<%=PortalConstants.OBLIGATION_ELEMENT_SEARCH%>', $('#searchobligationelement').val(), function(data) {
                if($dataTable) {
                    $dataTable.destroy();
                }
                $('#obligationElementSearchResultstable tbody').html(data);
                makeObligatiobElementsDataTable();
            });
        });

        $('#obligationElementSearchResultstable').on('change', 'input', function() {
            $dialog.enablePrimaryButtons($('#obligationElementSearchResultstable input:checked').length > 0);
        });

        $('#copyToClipboard').on('click', function(event) {
            let textSelector = "table tr td#documentId",
            textToCopy = $(textSelector).clone().children().remove().end().text().trim();
            clipboard.copyToClipboard(textToCopy, textSelector);
        });

        function makeObligatiobElementsDataTable() {
            $dataTable = datatables.create('#obligationElementSearchResultstable', {
                destroy: true,
                paging: false,
                info: false,
                language: {
                    emptyTable: "<liferay-ui:message key="no.obligation.element.found" />",
                    processing: "<liferay-ui:message key="processing" />",
                    loadingRecords: "<liferay-ui:message key="loading" />"
                },
                select: 'multi+shift'
            }, undefined, [0]);
            datatables.enableCheckboxForSelection($dataTable, 0);
        }

        function obligationElementContentFromAjax(what, where, callback) {
            $dialog.$.find('.spinner').show();
            $dialog.$.find('#obligationElementSearchResultstable').hide();
            $dialog.$.find('#searchbuttonobligation').prop('disabled', true);
            $dialog.enablePrimaryButtons(false);

            jQuery.ajax({
                type: 'POST',
                url: '<%=viewObligationELementURL%>',
                data: {
                    '<portlet:namespace/><%=PortalConstants.WHAT%>': what,
                    '<portlet:namespace/><%=PortalConstants.WHERE%>': where
                },
                success: function (data) {
                    callback(data);

                    $dialog.$.find('.spinner').hide();
                    $dialog.$.find('#obligationElementSearchResultstable').show();
                    $dialog.$.find('#searchbuttonobligation').prop('disabled', false);
                },
                error: function() {
                    $dialog.alert('Can not import Obligation ELement');
                }
            });
        }

        function showObligationElementDialog() {
            if($dataTable) {
                $dataTable.destroy();
                $dataTable = undefined;
            }

            $dialog = dialog.open('#searchObligationElementsDialog', {
            }, function(submit, callback) {
                var obligationElement = [];
                if($("input[type='radio'].form-check-input").is(':checked')) {
                    var selected_value =  $("input[type='radio'].form-check-input:checked").val();
                    obligationElement.push($("input[type='radio'].form-check-input:checked").attr("lang"));
                    obligationElement.push($("input[type='radio'].form-check-input:checked").attr("action"));
                    obligationElement.push($("input[type='radio'].form-check-input:checked").attr("object"));
                }
                // return obligation will be imported
                console.log(obligationElement)
                callback(true);
            }, function() {
                this.$.find('.spinner').hide();
                this.$.find('#obligationElementSearchResultstable').hide();
                this.$.find('#searchobligationelement').val('');
                this.enablePrimaryButtons(false);
            });
        }
    });
</script>
