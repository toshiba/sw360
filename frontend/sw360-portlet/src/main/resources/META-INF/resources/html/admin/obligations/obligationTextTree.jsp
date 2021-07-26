<%@include file="/html/init.jsp"%>
<%@ page import="org.eclipse.sw360.portal.users.UserCacheHolder" %>
<%@ page import="org.eclipse.sw360.datahandler.thrift.licenses.Obligation" %>	
<%@ page import="org.eclipse.sw360.datahandler.thrift.licenses.ObligationElement" %>
<%@ page import="org.eclipse.sw360.datahandler.thrift.licenses.ObligationNode" %>
<jsp:useBean id="obligationNodeList" type="java.util.List<org.eclipse.sw360.datahandler.thrift.licenses.ObligationNode>" scope="request"/>
<jsp:useBean id="obligationElementList" type="java.util.List<org.eclipse.sw360.datahandler.thrift.licenses.ObligationElement>" scope="request"/>

<div id="obligationTree">    
    <main class="container">
        <div class="wrapper">
            <div class="main-ctn">
                <div id="tree">
                    <h2>Input</h2>
                    <ul id="obligationText">
                        <li class="tree-node" id="root">
                            <input type="text" name="<portlet:namespace/><%=ObligationNode._Fields.NODE_TEXT%>" class="elementType" placeholder="Same with Obigation Title  ">
                            <span class="controls">
                                &raquo;
                                <a class="btn-link" href="#" data-func="add-child"
                                    >+child</a
                                >
                            </span>
                        </li>
                    </ul>
                    <ul id="tree" class="tree"></ul>
                </div>

                <hr />

                <div class="right">
                    <h2 class="app-subtitle">Preview</h2>

                    <div class="output-tree-ctn">
                        <pre id="out"></pre>
                    </div>
                </div>
            </div>
        </div>
    </main>

    <div class="hidden" id="template">
        <ul>
            <li class="tree-node">
                <input type="text" name="<portlet:namespace/><%=ObligationNode._Fields.NODE_TYPE%>" class="elementType" list="typeList" placeholder="Type">
                <datalist id="typeList">
                    <option value="Obligation">  
                </datalist>

                <%-- Obligation element --%>
                <input type="text" name="<portlet:namespace/><%=ObligationElement._Fields.LANG_ELEMENT%>" class="obLangElement" list="obLangElement" element-type="Obligation" placeholder="Language Element">
                <datalist id="obLangElement">
                    <option value="YOU MUST">  
                    <option value="YOU MUST NOT">
                </datalist>

                <input type="text" name="<portlet:namespace/><%=ObligationElement._Fields.ACTION%>" class="obAction" list="obAction" element-type="Obligation" placeholder="Action">
                <datalist id="obAction">
                    <option value="Provide">  
                    <option value="Modify">
                    <option value="Display">
                </datalist>

                <input type="text" name="<portlet:namespace/><%=ObligationElement._Fields.OBJECT%>" class="obObject" list="obObject" element-type="Obligation" placeholder="Object">
                <datalist id="obObject">
                    <option value="Copyright notices">  
                    <option value="License text">
                    <option value="License notices">
                </datalist>
                <%-- Other Type --%>
                <input type="text" name="<portlet:namespace/><%=ObligationNode._Fields.NODE_TEXT%>" class="other" element-type="Other" placeholder="Text">

                <%-- Action with element --%>
                <span class="controls">
                    &raquo;
                    <a class="btn-link" href="#"Attribute data-func="add-sibling"
                        >+sibling</a
                    >
                    |
                    <a class="btn-link" href="#" data-func="add-child"
                        >+child</a
                    >
                    |
                    <a class="btn-link" href="#" data-func="delete"
                        >delete</a
                    >
                    |
                    <a class="btn-link" href="#" data-func="import" id="importObligationElementtButton"
                        >import</a
                    >

                </span>
            </li>
        </ul>
    </div>
</div>
<%@ include file="/html/admin/obligations/includes/searchObligationElements.jsp" %>
<%@ include file="/html/utils/includes/requirejs.jspf" %>
<script>
require(['jquery', 'modules/dialog', 'bridges/datatables', 'utils/keyboard', /* jquery-plugins,  'jquery-ui',*/ 'utils/link', 'utils/includes/clipboard'], function($, dialog, datatables, keyboard, link, clipboard) {
    $(document).ready(function () {
        $("#root .elementType").first().prop('disabled', true);
        const selectElement = document.querySelector('#todoTitle');
        selectElement.addEventListener('change', (event) => {
            $("#root .elementType").first().val(document.getElementById('todoTitle').value)
            rebuild_tree()
        });
        addTypeSugguestions()
        addObligationSugguestions()
        // get sugguestions
        function addTypeSugguestions(){
            var typeSugguestion = new Set()
            <core_rt:forEach items="${obligationNodeList}" var="obligNode">
                typeSugguestion.add("<sw360:out value='${obligNode.nodeType}'/>")
            </core_rt:forEach>
            typeSugguestion.delete("ROOT")
            typeSugguestion.delete("Obligation")
            for (var type of typeSugguestion) {
                    document.getElementById("typeList").innerHTML +=
                    "<option value=\""+type+"\">"
            }
        }

        function addObligationSugguestions(){
            var languageElements = new Set()
            var actions = new Set()
            var objects = new Set()
            <core_rt:forEach items="${obligationElementList}" var="obligationElement">
                languageElements.add("<sw360:out value='${obligationElement.langElement}'/>")
                actions.add("<sw360:out value='${obligationElement.action}'/>")
                objects.add("<sw360:out value='${obligationElement.object}'/>")
            </core_rt:forEach>
            languageElements.delete("YOU MUST")
            languageElements.delete("YOU MUST NOT")
            for (var languageElement of languageElements) {
                document.getElementById("obLangElement").innerHTML +=
                "<option value=\""+languageElement+"\">"
            }
            actions.delete("Provide")
            actions.delete("Modify")
            actions.delete("Display")
            for (var action of actions) {
                document.getElementById("obAction").innerHTML +=
                "<option value=\""+action+"\">"
            }
            objects.delete("Copyright notices")
            objects.delete("License text")
            objects.delete("License notices")
            for (var object of objects) {
                document.getElementById("obObject").innerHTML +=
                "<option value=\""+object+"\">"
            }
        }
        const prefix = "|-- ",
            prefix_last = "`-- ",
            spacer = "|   ",
            spacer_e = "    ",
            ul_template = $("#template > ul"),
            li_template = $("li", ul_template).first();

        $(".elementType").each(function() {
            showElementControls($(this));
        });

        function changeElementType() {
            showElementControls($(this));
        }

        function showElementControls(typeControl) {
            var type = typeControl.val();
            var siblings = typeControl.siblings();
            siblings.hide();

            if (type == 'Obligation') {
                $.each(siblings, function(key, sibling) {
                    if ($(sibling).attr('element-type') == type) {
                        $(sibling).show();
                    }
                });
            } else {
            $.each(siblings, function(key, sibling) {
                    if ($(sibling).attr('element-type') == undefined) {
                        $(sibling).hide();
                        return;
                    }
                    if ($(sibling).attr('element-type') != 'Obligation') {
                        $(sibling).show();
                    }
                });
            }

            typeControl.siblings().each(function(key, element) {
                if($(element).is('ul')) {
                    $(element).css('display', '');
                }
            });
        }

        const action = {
            "add-sibling": function (obj) {
                var newNode = ul_template.clone();
                obj.parent().after(newNode);
                $('.elementType').on('change keyup', changeElementType);
                showElementControls(newNode.find(".elementType"));
            },
            "add-child": function (obj) {
                console.log(obj);
                var newNode = ul_template.clone();
                obj.append(newNode);
                $('.elementType').on('change keyup', changeElementType);
                showElementControls(newNode.find(".elementType"));
            },
            delete: function (obj) {
                obj.parent().remove();
            },
            "import": function (obj) {
                $dialog = dialog.open('#searchObligationElementsDialog')
            },
        };

        $(document).on("click", "li.tree-node .controls > a", function () {
            action[this.getAttribute("data-func")]($(this).closest("li"));
            rebuild_tree();
            return false;
        });

        function get_subdir_text(obj, pad) {
            let padding = pad || "",
                out = "",
                items = obj.children("li"),
                last = items.length - 1;

            items.each(function (index) {
                const $this = $(this);
                if ($this.attr('id') != "root") {
                    if ($this.children(".elementType").val() == "Obligation") {
                        out +=
                        padding +
                        ($this.children(".obLangElement").val() == null ? "" : $this.children(".obLangElement").val()) + " " +
                        ($this.children(".obAction").val() == null ? "" : $this.children(".obAction").val()) + " " +
                        ($this.children(".obObject").val() == null ? "" : $this.children(".obObject").val()) + " " +
                        "\n";
                    } else {
                        out +=
                        padding +
                        $this.children(".elementType").val() + " " +
                        ($this.children(".other").val() == null ? "" : $this.children(".other").val()) + " " +
                        "\n";
                    }
                }

                const subdirs = $this.children("ul");
                if (subdirs.length) {
                    out += get_subdir_text(
                        subdirs,
                        padding +
                        spacer_e
                    );
                }
            });
            return out;
        }

        function rebuild_tree() {
            $("#out").text(get_subdir_text($("#obligationText")));
        }

        // $("#tree").append(li_template.clone());
        $(document).on("keyup", "#tree input", rebuild_tree);
        //$("#p_name").on("keyup", rebuild_tree);

        $(document)
            .on("mouseover", "li, #tree", function (e) {
                $(this).children(".controls").show();
                e.stopPropagation();
            })
            .on("mouseout", "li, #tree", function (e) {
                $(this).children(".controls").hide();
                e.stopPropagation();
            });
        rebuild_tree();
    });
});
</script>