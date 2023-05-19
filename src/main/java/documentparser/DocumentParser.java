package documentparser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

public class DocumentParser
{
    public final static class FunctionParDef
    {
        public String obj = "";
        public String objOriginal = "";
        public String name = "";
        public String desc = "";
    }

    public static class FunctionDef
    {
        public String name;
        public java.util.ArrayList<FunctionParDef> parameters;
        public java.util.HashMap<String, String> parameterDetails;
        public String description;
        public FunctionParDef returns;
        public boolean isstatic;
        public String argsstring;

        public FunctionDef(String name)
        {
            this.name = name;
            parameters = new java.util.ArrayList<>();
            parameterDetails = new HashMap<>();
            description = "";
            returns = new FunctionParDef();
            returns.obj = "";
            returns.name = "";
            isstatic = false;
            argsstring = "";
        }
    }

    public static class MemberDef
    {
        public String name;
        public String type;
        public String description;
        public String init;
        public boolean isstatic;

        public MemberDef(String name)
        {
            this.name = name;
            description = "";
            init = "";
            isstatic = false;
        }
    }

	static boolean deleteDirectory(File directoryToBeDeleted) {
		File[] allContents = directoryToBeDeleted.listFiles();
		if (allContents != null) {
			for (File file : allContents) {
				DocumentParser.deleteDirectory(file);
			}
		}
		return directoryToBeDeleted.delete();
	}

    public static String outputFolder = "output";
    public static StringBuilder global_lua_append = null;
    public static String ext = ".lua";
    public static String clsname = "";
    public static boolean extended = true;
    public static void main(String[] args) throws Exception
    {
    	if(args.length == 0)
		{
			System.out.println("Usage");
			System.out.println("parser.jar parseType inputPath (outputPath)");
			return;
		}
    	System.out.println("Parsing started");
		String path = args[1];
		if(args.length > 2)
		{
			outputFolder = args[2];
			System.out.println("outputFolder " + outputFolder);
		}
		if(args.length > 3)
		{
			clsname = args[3];
			System.out.println("clsname " + clsname);
		}
		if(args.length > 4)
		{
			extended = Integer.parseInt(args[4]) == 1;
			System.out.println("extended " + extended);
		}
    	if(args[0].equals("0"))
		{
			System.out.println("Doc parsing started at path " + path);
			File f = new File(outputFolder);
			if (f.isDirectory())
			{
				f.delete();
			}
			f.mkdir();
			if(extended)
			{
				File fJson = new File(outputFolder + "Json");
				if (fJson.isDirectory())
				{
					fJson.delete();
				}
				fJson.mkdir();
				fJson = new File(outputFolder + "Shared");
				if (fJson.isDirectory())
				{
					fJson.delete();
				}
				fJson.mkdir();
				fJson = new File(outputFolder + "Android");
				if (fJson.isDirectory())
				{
					fJson.delete();
				}
				fJson.mkdir();
			}
			RunParserForFolder(path);
		}
		else if(args[0].equals("1"))
		{
			System.out.println("Resource parsing started at path " + path);
			ResourceLoader(path, false, clsname);
		}
		else
		{
			System.out.println("Resource parsing started at path " + path + " kotlin");
			ResourceLoader(path, true, clsname);
		}
    }

    private static StringBuilder getText(Node node, StringBuilder text)
	{
		if(node == null)
			return text;

		if(text == null)
			text = new StringBuilder();

		if(node.getNodeValue() != null)
		{
			String toAppend = node.getNodeValue().trim();
			if(text.length() > 0 && toAppend.length() > 0)
				text.append(" ");
			text.append(toAppend);
		}

		text = getText(node.getFirstChild(), text);

 		text = getText(node.getNextSibling(), text);

		return text;
	}

	private static FunctionDef ParseFunction(Node node)
	{
		boolean IsStatic = node.getAttributes().getNamedItem("static").getNodeValue().equals("yes");
		FunctionDef fd = new FunctionDef("");
		if(node.getFirstChild() != null)
		{
		    Node child = node.getFirstChild();
			do
			{
				if (child.getNodeName().equals("name"))
				{
					fd.name = child.getFirstChild().getNodeValue();
				}
				else if(child.getNodeName().equals("argsstring"))
				{
					fd.argsstring = child.getFirstChild().getNodeValue();
				}
				else if(child.getNodeName().equals("type"))
				{
					if(child.getFirstChild() != null)
						fd.returns.obj = getText(child.getFirstChild(), null).toString();
				}
				else if (child.getNodeName().equals("param"))
				{
				    Node param = child.getFirstChild();
					if (param != null)
					{
						FunctionParDef fpd = new FunctionParDef();
						do
						{
							if (param.getNodeName().equals("type"))
							{
							    Node type = param.getFirstChild();
							    fpd.obj = getText(type, null).toString();
							    fpd.objOriginal = getText(type, null).toString();
							}
							else if (param.getNodeName().equals("declname"))
							{
                                Node decn = param.getFirstChild();
                                fpd.name = getText(decn, null).toString();
							}
                            param = param.getNextSibling();
						} while (param != null);
						fd.parameters.add(fpd);
					}
				}
				else if (child.getNodeName().equals("detaileddescription"))
				{
				    Node para = child.getFirstChild();
					if (para != null) //para
					{
					    Node inner = para.getNextSibling();
						if (inner != null) //inner
						{
							String funcDescription = "";
                            Node fdn = inner.getFirstChild();
							do
							{
								boolean skip = false;
								if (fdn.getNodeName().equals("parameterlist"))
								{
									skip = true;
									if(fdn.getAttributes().getNamedItem("kind") != null && fdn.getAttributes().getNamedItem("kind").getNodeValue().equals("param"))
                                    {
                                        Node parameterItem = fdn.getFirstChild();
                                        while (parameterItem != null)
                                        {
                                            Node parameterItemChild = parameterItem.getFirstChild();
											String key = "";
                                            while (parameterItemChild != null)
                                            {
                                                if(parameterItemChild.getNodeName().equals("parameternamelist"))
                                                {
                                                	key = getText(parameterItemChild.getFirstChild(), null).toString();
                                                }
                                                else if(parameterItemChild.getNodeName().equals("parameterdescription"))
                                                {
                                                	String val = getText(parameterItemChild.getFirstChild(), null).toString();
													fd.parameterDetails.put(key, val);
                                                }
                                                parameterItemChild = parameterItemChild.getNextSibling();
                                            }
                                            parameterItem = parameterItem.getNextSibling();
                                        }
                                    }
								}
								else if (fdn.getNodeName().equals("ref"))
                                {
                                    skip = true;
                                    funcDescription += fdn.getFirstChild().getNodeValue();
                                }
								else if (fdn.getNodeName().equals("simplesect")) //return
								{
									skip = true;

									Node fdnPara = fdn.getFirstChild();
									if (fdnPara != null) //para
									{
									    Node fdnParaRef = fdnPara.getFirstChild();
										if (fdnParaRef != null) //ref
										{
											do
											{
												if (fdnParaRef.getNodeName().equals("ref") && fdnParaRef.getFirstChild() != null && fdnParaRef.getFirstChild().getNodeValue() != null)
												{
													fd.returns.obj = fdnParaRef.getFirstChild().getNodeValue();
												}
												else
												{
													fd.returns.name += fdnParaRef.getNodeValue();
												}
												fdnParaRef = fdnParaRef.getNextSibling();
											} while (fdnParaRef != null);
										}
									}
								}
								if (!skip)
								{
									funcDescription += fdn.getNodeValue();
								}
								fdn = fdn.getNextSibling();
							} while (fdn != null);

							if (!funcDescription.trim().equals("(Ignore)"))
							{
								fd.isstatic = IsStatic;
								fd.description = funcDescription.trim();
							}
						}
					}
				}
				child = child.getNextSibling();
			} while (child != null);

			if (fd.description.equals(""))
			{
				return null;
			}
			return fd;
		}

		return null;
	}

	private static MemberDef ParseVariable(Node node)
	{
        boolean IsStatic = node.getAttributes().getNamedItem("static").getNodeValue().equals("yes");
		MemberDef md = new MemberDef("");
        if(node.getFirstChild() != null)
        {
            Node child = node.getFirstChild();
			do
			{
				if (child.getNodeName().equals("name"))
				{
					md.name = child.getFirstChild().getNodeValue();
				}
				else if(child.getNodeName().equals("type"))
				{
					md.type = getText(child.getFirstChild(), null).toString();
				}
				else if (child.getNodeName().equals("detaileddescription"))
				{
				    Node para = child.getFirstChild();
					if (para != null) //para
					{
						md.isstatic = IsStatic;
						md.description = getText(para, null).toString();
					}
				}
				else if (child.getNodeName().equals("initializer"))
				{
					Node para = child.getFirstChild();
					if (para != null) //para
					{
						md.init = getText(para, null).toString();
					}
				}
				child = child.getNextSibling();
			} while (child != null);

			return md;
		}

		return null;
	}

    private static void RunParserForFolder(String p) throws Exception
    {
        String splitMatch = "::";
        File dir = new File(p);
        for (File file : dir.listFiles())
        {
            String filename = file.getName();
            if (filename.startsWith("classdev_1_1topping_1_1android_1_1")
					|| filename.startsWith("enumdev_1_1topping_1_1android_1_1")
					|| filename.startsWith("classandroid_1_1widget_1_1")
					|| filename.startsWith("classandroidx_1_1fragment_1_1app_1_1"))
            {
                if (filename.contains("lua_class")
						|| filename.contains("lua_engine")
						|| filename.contains("display_metrics")
						|| filename.contains("lua_function")
						|| filename.contains("lua_interface")
						|| filename.contains("lua_java_function")
						|| filename.contains("lua_object_3")
						|| filename.contains("lunar")
						|| filename.contains("enum")
						|| filename.contains("lua_tasker")
						|| filename.contains("lua_object")
						|| filename.contains("lua_load_handler")
						|| filename.contains("lua_helper")
						|| filename.contains("osspecific"))
				{
					continue;
				}

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(file);
                XPathFactory xPathfactory = XPathFactory.newInstance();
                XPath xpath = xPathfactory.newXPath();
                XPathExpression kindexpr = xpath.compile("/doxygen/compounddef");

                XPathExpression expr = xpath.compile("/doxygen/compounddef/compoundname");

				XPathExpression baseexpr = xpath.compile("/doxygen/compounddef/basecompoundref");

                NodeList ns = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
                String module = null;
                String pack = null;
                for(int i = 0; i < ns.getLength(); i++)
                {
                    Node n = ns.item(i);
                    String name = n.getFirstChild().getNodeValue();
                    String[] nameArr = name.split(splitMatch);
                    module = nameArr[nameArr.length - 1];
                    pack = name.replace("::", ".");
                }

                NodeList nsBase = (NodeList) baseexpr.evaluate(doc, XPathConstants.NODESET);
                String base = "";
                if(nsBase.getLength() > 0)
				{
					Node n = nsBase.item(0);
					String name = getText(n, null).toString();
					String[] nameSpaceArr = name.split(" ");
					if(nameSpaceArr.length > 0)
					{
						String[] nameArr = nameSpaceArr[0].split("\\.");
						if (nameArr.length > 0)
							base = nameArr[nameArr.length - 1];
					}
				}

                String kindOfModule = "";
                NodeList nsModule = (NodeList) kindexpr.evaluate(doc, XPathConstants.NODESET);
                for(int i = 0; i < nsModule.getLength(); i++)
				{
					Node n = nsModule.item(i);
					kindOfModule = n.getAttributes().getNamedItem("kind").getNodeValue();
					break;
				}

                if (!(module.startsWith("Lua") || module.startsWith("LG")))
                {
                    if (!kindOfModule.equals("enum"))
                    {
                        System.out.println("non enum skipping...");
                        continue;
                    }
                    else
                    {
                        System.out.println("enum found...");
                    }
                }

				expr = xpath.compile("/doxygen/compounddef/detaileddescription/para");
				String description = "";
                nsModule = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
                for(int i = 0; i < nsModule.getLength(); i++)
                {
                    description = nsModule.item(i).getNodeValue();
                }
                if(description == null)
                    description = "";

                expr = xpath.compile("/doxygen/compounddef/sectiondef/memberdef");
                nsModule = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
                java.util.ArrayList<FunctionDef> functions = new java.util.ArrayList<FunctionDef>();
				java.util.ArrayList<MemberDef> variables = new java.util.ArrayList<MemberDef>();
                for(int i = 0; i < nsModule.getLength(); i++)
                {
                    Node n = nsModule.item(i);
                    String kind = n.getAttributes().getNamedItem("kind").getNodeValue();

                    if (kind.equals("function"))
					{
						FunctionDef fd = ParseFunction(n);
						if(fd != null)
						{
							functions.add(fd);
						}
						continue;
					}
					else if (kind.equals("variable"))
					{
						MemberDef md = ParseVariable(n);
						if (md != null && !md.description.equals("") && md.isstatic)
						{
							variables.add(md);
						}
						continue;
					}
                }

				if (description.contains("(Global)"))
				{
					if (global_lua_append == null)
					{
						global_lua_append = new StringBuilder();
					}
					for (MemberDef md : variables)
					{
						global_lua_append.append("------------------------------------------------------------------------------").append("\n");
						global_lua_append.append("-- ").append(md.description).append("\n");
						global_lua_append.append("-- @field[parent = #global] ").append(md.name).append("#").append(md.name).append(" ").append(md.name).append(" preloaded module").append("\n");
						global_lua_append.append("\n");
					}
					variables.clear();
				}

				System.out.println(module);
				WriteToFile(module, pack, description, base, functions, variables);
            }
        }

        WriteAll();
    }

	public static String GetLuaType(String type, String extendedType)
    {
		if(extendedType != null && extendedType.startsWith("+") && !extendedType.equals(""))
		{
			extendedType = extendedType.substring(1);
			if(extendedType.startsWith("+fun("))
			{
				StringBuilder sb = new StringBuilder();
				String funParam = extendedType.substring(extendedType.indexOf('(') + 1, extendedType.indexOf(')'));
				String[] funParamArr = funParam.split(",");
				StringBuilder sbIn = new StringBuilder();
				sbIn.append("\"function(");
				for (int i = 0; i < funParamArr.length; i++)
				{
					String[] kv = funParamArr[i].split(":");
					sbIn.append(kv[0].trim());

					if (i != funParamArr.length - 1)
						sbIn.append(", ");
				}
				sbIn.append(") end\"");
				sb.append("(").append(extendedType).append(") | ").append(sbIn).append(" | LuaTranslator");
				return sb.toString();
			}
			else
				return extendedType;
		}
        else if (type.equals("int") || type.equals("Integer") || type.equals("float") || type.equals("Float") || type.equals("double") || type.equals("Double"))
        {
            return "number";
        }
        else if (type.equals("String") || type.equals("string"))
        {
            return "string";
        }
        else if (type.equals("boolean") || type.equals("bool"))
        {
            return "boolean";
        }
        else if(type.equals("Object"))
        {
            return "userdata";
        }
        else if(type.startsWith("HashMap"))
		{
			String typeIn = type.replaceAll("\\s+","");
			typeIn = typeIn.substring(7);
			String[] arr = typeIn.split(",");
			String left = arr[0].substring(1);
			left = GetLuaType(left, null);
			String right = arr[1].substring(0, arr[1].length() - 1);
			right = GetLuaType(right, null);
			return "table<" + left + "," + right + ">";
		}
        else if(type.startsWith("ArrayList"))
		{
			String typeIn = type.replaceAll("\\s+","");
			typeIn = typeIn.substring(10, typeIn.length() - 1);
			String center = GetLuaType(typeIn, null);
			return center + "[]";
		}
        else
        	return type;
    }

    private static void SetBase(JSONObject obj, String base)
	{
		if(base.equals(""))
			return;

		JSONObject objBase = jsonMap.get(base);
		if(objBase == null)
			return;

		JSONArray funcs = obj.getJSONArray("functions");
		JSONArray bfuncs = objBase.getJSONArray("functions");

		JSONArray vars = obj.getJSONArray("variables");
		JSONArray bvars = objBase.getJSONArray("variables");


		for(int i = 0; i < bfuncs.length(); i++)
		{
			JSONObject bfunc = bfuncs.getJSONObject(i);
			boolean found = false;
			for(int j = 0; j < funcs.length(); j++)
			{
				JSONObject func = funcs.getJSONObject(j);
				if(func.getString("name").equals(bfunc.getString("name")))
				{
					found = true;
					break;
				}
			}
			if(!found)
				funcs.put(bfunc);
		}

		vars.putAll(bvars);

		obj.put("functions", funcs);
		obj.put("methods", vars);

		JSONArray arr = null;
		if(obj.has("baseArr"))
			arr = obj.getJSONArray("baseArr");
		else
		{
			arr = new JSONArray();
		}

		arr.put(base);

		obj.put("baseArr", arr);

		String baseBase = objBase.getString("base");
		if(baseBase.equals(""))
			return;

		SetBase(obj, baseBase);
	}

    private static void WriteAll()
    {
		for (java.util.Map.Entry<String, String> kvp : fileMap.entrySet())
		{

            FileOutputStream fs = null;
            try
            {
                fs = new FileOutputStream(outputFolder + "/" + kvp.getKey() + ext);
                fs.write(kvp.getValue().getBytes());
                fs.flush();
                fs.close();
            }
            catch (Exception e)
            {

            }
		}

		if(!extended)
			return;

		for (java.util.Map.Entry<String, String> kvp : sharedMap.entrySet())
		{

			FileOutputStream fs = null;
			try
			{
				fs = new FileOutputStream(outputFolder + "Shared" + "/" + kvp.getKey() + ".kt");
				fs.write(kvp.getValue().getBytes());
				fs.flush();
				fs.close();
			}
			catch (Exception e)
			{

			}
		}

		for (java.util.Map.Entry<String, String> kvp : androidMap.entrySet())
		{

			FileOutputStream fs = null;
			try
			{
				fs = new FileOutputStream(outputFolder + "Android" + "/" + kvp.getKey() + ".kt");
				fs.write(kvp.getValue().getBytes());
				fs.flush();
				fs.close();
			}
			catch (Exception e)
			{

			}
		}

		for (java.util.Map.Entry<String, JSONObject> kvp : jsonMap.entrySet())
		{
			JSONObject ob = kvp.getValue();
			String base = ob.getString("base");
			if(!jsonMap.containsKey(base))
			{
				ob.put("base", "");
				JSONArray jsoA = new JSONArray();
				jsoA.put(kvp.getKey());
				jsoA.put("NativeObject");
				ob.put("baseArr", jsoA);
				continue;
			}
			SetBase(ob, base);
			JSONArray arrBase = ob.getJSONArray("baseArr");
			JSONArray arrBaseNew = new JSONArray();
			arrBaseNew.put(kvp.getKey());
			for(int i = 0; i < arrBase.length(); i++)
			{
				arrBaseNew.put(arrBase.get(i));
			}
			arrBaseNew.put("NativeObject");
			ob.put("baseArr", arrBaseNew);
		}

		JSONArray jsoMain = new JSONArray();
		for (java.util.Map.Entry<String, JSONObject> kvp : jsonMap.entrySet())
		{
			jsoMain.put(kvp.getKey());
			FileOutputStream fs = null;
			try
			{
				fs = new FileOutputStream(outputFolder + "Json/" + kvp.getKey() + ".json");
				fs.write(kvp.getValue().toString().getBytes());
				fs.flush();
				fs.close();
			}
			catch (Exception e)
			{

			}
		}

		JSONObject jso = new JSONObject();
		jso.put("modules", jsoMain);

		try
		{
			FileOutputStream fs = new FileOutputStream(outputFolder + "Json/modules.json");
			fs.write(jso.toString().getBytes());
			fs.flush();
			fs.close();
		}
		catch (Exception e)
		{

		}
    }

    public static java.util.HashMap<String, String> fileMap = new java.util.HashMap<String, String>();
    public static java.util.HashMap<String, JSONObject> jsonMap = new java.util.HashMap<String, JSONObject>();
    public static java.util.HashMap<String, String> androidMap = new java.util.HashMap<String, String>();
    public static java.util.HashMap<String, String> sharedMap = new java.util.HashMap<String, String>();
    public static java.util.HashMap<String, String> iosMap = new java.util.HashMap<String, String>();

    private static void WriteToFile(String module, String pack, String description, String base, ArrayList<FunctionDef> functions, ArrayList<MemberDef> variables)
    {
		/*if (functions.isEmpty() && variables.isEmpty())
		{
			return;
		}*/

		StringBuilder sw = new StringBuilder();

		boolean appendIntro = true;
		if (fileMap.containsKey(module))
		{
			sw.append(fileMap.get(module));
			fileMap.remove(module);
			appendIntro = false;
		}

		if(appendIntro)
		{
			if (global_lua_append == null)
			{
				global_lua_append = new StringBuilder();
			}

			global_lua_append.append("------------------------------------------------------------------------------").append("\n");
			global_lua_append.append("-- ").append(description).append("\n");
			global_lua_append.append("-- @field[parent = #global] ").append(module).append("#").append(module).append(" ").append(module).append(" preloaded module");
			global_lua_append.append("\n");

			sw.append("-------------------------------------------------------------------------------").append("\n");

			sw.append(description).append("\n");

			sw.append("---@class " + module + ((base != null) ? (":" + base) : ""));
			if(description != null && !description.equals(""))
				sw.append(" @").append(description);
			sw.append("\n");
			sw.append("local " + module + " = Class()").append("\n");

			sw.append("\n");
		}

		for (FunctionDef fd : functions)
		{
			sw.append("-------------------------------------------------------------------------------").append("\n");

			sw.append("---@function " + fd.description).append("\n");
			boolean first = true;
			String funcLua = "function " + module + (fd.isstatic ? "." : ":") + fd.name + "(";

			for (FunctionParDef fpd : fd.parameters)
			{

				sw.append("---@param " + fpd.name + " " + GetLuaType(fpd.obj, fd.parameterDetails.get(fpd.name))).append("\n");
				if (first)
				{
					first = false;
					funcLua += fpd.name;
				}
				else
				{
					funcLua += "," + fpd.name;
				}
			}
			funcLua += ")\nend";
			if (!fd.returns.obj.equals("") && !fd.returns.obj.equals("void"))
			{
				sw.append("---@return " + GetLuaType(fd.returns.obj, null)).append("\n");
			}
			else
			{
				if (!fd.returns.name.equals(""))
				{
					String[] arrIn = fd.returns.name.split("[ ]", -1);
					String returnType = arrIn[0];
					sw.append("---@return " + GetLuaType(returnType.trim(), null));
				}
			}
			sw.append(funcLua).append("\n");
			sw.append("\n");
		}

		for (MemberDef md : variables)
		{
			sw.append("-------------------------------------------------------------------------------").append("\n");
            sw.append("--- " + md.description).append("\n");

			String funcLua = module + "." + md.name + " " + md.init;
			sw.append(funcLua).append("\n");
			sw.append("\n");
		}

		sw.append("_G['").append(module).append("'] = ").append(module).append("\n");
		sw.append("return " + module).append("\n");

		fileMap.put(module, sw.toString());

        appendIntro = true;
        if (jsonMap.containsKey(module))
        {
            sw.append(jsonMap.get(module));
            jsonMap.remove(module);
            appendIntro = false;
        }

		JSONObject jso = new JSONObject();
        if(appendIntro)
        {
        	jso.put("name", module);
        	jso.put("description", description != null ? description : "");
        	jso.put("base", base);
        }

		JSONArray fns = new JSONArray();
        for (FunctionDef fd : functions)
        {
        	JSONObject function = new JSONObject();
        	function.put("name", fd.name);
        	function.put("static", fd.isstatic);
        	function.put("description", fd.description);
			function.put("argsstring", fd.argsstring);

        	JSONArray parameters = new JSONArray();
            for (FunctionParDef fpd : fd.parameters)
            {
            	JSONObject parameter = new JSONObject();
            	parameter.put("name", fpd.name);
            	parameter.put("type", GetLuaType(fpd.obj, fd.parameterDetails.get(fpd.name)));
				parameter.put("typeOrg", fpd.objOriginal);
                parameters.put(parameter);
            }
            function.put("parameters", parameters);

            if (!fd.returns.obj.equals("") && !fd.returns.obj.equals("void"))
            {
				function.put("return", GetLuaType(fd.returns.obj, null));
				function.put("returnOrg", fd.returns.obj);
            }
            else
			{
				if (!fd.returns.name.equals(""))
				{
					String[] arrIn = fd.returns.name.split("[ ]", -1);
					String returnType = arrIn[0];
					function.put("return", GetLuaType(returnType.trim(), null));
					function.put("returnOrg", returnType.trim());
				}
				else
				{
					function.put("return", "");
					function.put("returnOrg", "void");
				}
			}

            fns.put(function);
        }
        jso.put("functions", fns);

        JSONArray vars = new JSONArray();
        for (MemberDef md : variables)
        {
        	JSONObject variable = new JSONObject();
        	variable.put("name", md.name);
        	variable.put("type", GetLuaType(md.type.trim(), null));
			variable.put("typeOrg", md.type.trim());
        	variable.put("static", md.isstatic);
        	variable.put("description", md.description);
        	variable.put("default", md.init);
        	vars.put(variable);
        }
        jso.put("variables", vars);

        jsonMap.put(module, jso);

		{
			sw = new StringBuilder();

			sw.append("package org.sombrenuit.dk.luaandroidkotlin.shared").append("\n\n");
			boolean baseExists = base != null && !base.equals("") && !base.equals("LuaInterface") && (base.startsWith("LG") || base.startsWith("Lua"));
			if (baseExists)
				sw.append("expect open class ").append(module).append(" : ").append(base).append("\n");
			else
				sw.append("expect open class ").append(module).append("\n");
			sw.append("{").append("\n");

			ArrayList<MemberDef> staticVars = new ArrayList<>();
			ArrayList<MemberDef> instanceVars = new ArrayList<>();
			for (MemberDef md : variables)
			{
				if (md.isstatic)
					staticVars.add(md);
				else
					instanceVars.add(md);
			}

			ArrayList<FunctionDef> staticFuncs = new ArrayList<>();
			ArrayList<FunctionDef> instanceFuncs = new ArrayList<>();
			for (FunctionDef fd : functions)
			{
				if (fd.isstatic)
					staticFuncs.add(fd);
				else
					instanceFuncs.add(fd);
			}
			if (staticFuncs.size() > 0 || staticVars.size() > 0)
			{
				sw.append("   companion object {").append("\n");
				for (MemberDef md : staticVars)
				{
					String type = md.type;
					if (type.contains("final"))
						type = type.replace("final", "").trim();
					sw.append("        val ").append(md.name).append(": ").append(getKotlinObject(type, null))/*.append(" ").append(md.init)*/.append("\n");
				}
				for (FunctionDef fd : staticFuncs)
				{
					sw.append("        fun ").append(fd.name).append("(");
					for (int i = 0; i < fd.parameters.size(); i++)
					{
						FunctionParDef fdp = fd.parameters.get(i);
						String parName = fdp.name;
						if (parName.equals("var") || parName.equals("val"))
							parName = "v";
						sw.append(parName).append(": ").append(getKotlinObject(fdp.objOriginal, fdp.obj));
						if (i != fd.parameters.size() - 1)
						{
							sw.append(", ");
						}
					}
					if (fd.returns != null && !fd.returns.objOriginal.equals("LuaInterface"))
					{
						String retKot = getKotlinObject(fd.returns.objOriginal, fd.returns.obj);
						if (!retKot.equals(""))
						{
							if(!retKot.endsWith("?"))
								retKot += "?";
							sw.append(")").append(": ").append(retKot).append("\n");
						}
						else
							sw.append(")").append("\n");
					}
					else
						sw.append(")").append("\n");
				}
				sw.append("   }").append("\n");
			}
			for (FunctionDef fd : instanceFuncs)
			{
				sw.append("   fun ").append(fd.name).append("(");
				for (int i = 0; i < fd.parameters.size(); i++)
				{
					FunctionParDef fdp = fd.parameters.get(i);
					String parName = fdp.name;
					if (parName.equals("var") || parName.equals("val"))
						parName = "v";
					sw.append(parName).append(": ").append(getKotlinObject(fdp.objOriginal, fdp.obj));
					if (i != fd.parameters.size() - 1)
					{
						sw.append(", ");
					}
				}
				if (fd.returns != null && !fd.returns.objOriginal.equals("LuaInterface"))
				{
					String retKot = getKotlinObject(fd.returns.objOriginal, fd.returns.obj);
					if (!retKot.equals(""))
					{
						if(!retKot.endsWith("?"))
							retKot += "?";
						sw.append(")").append(": ").append(retKot).append("\n");
					}
					else
						sw.append(")").append("\n");
				}
				else
					sw.append(")").append("\n");
			}
			sw.append("}");
			sharedMap.put(module, sw.toString());
		}

		{
			sw = new StringBuilder();

			sw.append("package org.sombrenuit.dk.luaandroidkotlin.shared").append("\n\n");
			boolean baseExists = base != null && !base.equals("") && !base.equals("LuaInterface") && (base.startsWith("LG") || base.startsWith("Lua"));
			if (baseExists)
				sw.append("actual open class ").append(module).append(" : ").append(base).append("()").append("\n");
			else
				sw.append("actual open class ").append(module).append("\n");
			sw.append("{").append("\n");
			String moduleCamel = "";
			if(module.startsWith("LG"))
			{
				moduleCamel = "lg" + module.substring(2);
			}
			else
			{
				moduleCamel = "lua" + module.substring(3);
			}
			sw.append("   private var ").append(moduleCamel).append(": ").append(pack).append("? = null").append("\n");

			ArrayList<MemberDef> staticVars = new ArrayList<>();
			ArrayList<MemberDef> instanceVars = new ArrayList<>();
			for (MemberDef md : variables)
			{
				if (md.isstatic)
					staticVars.add(md);
				else
					instanceVars.add(md);
			}

			ArrayList<FunctionDef> staticFuncs = new ArrayList<>();
			ArrayList<FunctionDef> instanceFuncs = new ArrayList<>();
			for (FunctionDef fd : functions)
			{
				if (fd.isstatic)
					staticFuncs.add(fd);
				else
					instanceFuncs.add(fd);
			}
			if (staticFuncs.size() > 0 || staticVars.size() > 0)
			{
				sw.append("   actual companion object {").append("\n");
				for (MemberDef md : staticVars)
				{
					String type = md.type;
					if (type.contains("final"))
						type = type.replace("final", "").trim();
					sw.append("        actual val ").append(md.name).append(": ").append(getKotlinObject(type, null)).append(" ").append(md.init).append("\n");
				}
				for (FunctionDef fd : staticFuncs)
				{
					sw.append("        actual fun ").append(fd.name).append("(");
					for (int i = 0; i < fd.parameters.size(); i++)
					{
						FunctionParDef fdp = fd.parameters.get(i);
						String parName = fdp.name;
						if (parName.equals("var") || parName.equals("val"))
							parName = "v";
						sw.append(parName).append(": ").append(getKotlinObject(fdp.objOriginal, fdp.obj));
						if (i != fd.parameters.size() - 1)
						{
							sw.append(", ");
						}
					}
					boolean rets = false;
					String retKot = "";
					if (fd.returns != null && !fd.returns.objOriginal.equals("LuaInterface"))
					{
						retKot = getKotlinObject(fd.returns.objOriginal, fd.returns.obj);
						if (!retKot.equals(""))
						{
							rets = true;
							sw.append(")").append(": ").append(retKot).append("\n");
						}
						else
						{
							rets = false;
							sw.append(")").append("\n");
						}
					}
					else
						sw.append(")").append("\n");
					sw.append("        {").append("\n");
					if(rets)
					{
						sw.append("            val pobj = ").append(module).append("()").append("\n");
						sw.append("            val pres = ");
					}
					else
						sw.append("            ");
					sw.append(pack).append(".").append(fd.name).append("(");
					for (int i = 0; i < fd.parameters.size(); i++)
					{
						FunctionParDef fdp = fd.parameters.get(i);
						String parName = fdp.name;
						if (parName.equals("var") || parName.equals("val"))
							parName = "v";
						sw.append(parName);
						String kType = getKotlinObject(fdp.objOriginal, fdp.obj);
						if(!isKotlinPrimitive(kType, null))
							sw.append("?.GetNativeObject()");
						if (i != fd.parameters.size() - 1)
						{
							sw.append(", ");
						}
					}
					sw.append(")").append("\n");
					if(rets)
					{
						sw.append("            pobj.SetNativeObject(pres").append((!retKot.startsWith("Any") ? "" : (" as " + pack))).append(")").append("\n");
						sw.append("            return pobj").append("\n");
					}
					sw.append("        }").append("\n");
				}
				sw.append("   }").append("\n");
			}
			for (FunctionDef fd : instanceFuncs)
			{
				sw.append("   actual fun ").append(fd.name).append("(");
				for (int i = 0; i < fd.parameters.size(); i++)
				{
					FunctionParDef fdp = fd.parameters.get(i);
					String parName = fdp.name;
					if (parName.equals("var") || parName.equals("val"))
						parName = "v";
					sw.append(parName).append(": ").append(getKotlinObject(fdp.objOriginal, fdp.obj));
					if (i != fd.parameters.size() - 1)
					{
						sw.append(", ");
					}
				}
				if (fd.returns != null && !fd.returns.objOriginal.equals("LuaInterface"))
				{
					String retKot = getKotlinObject(fd.returns.objOriginal, fd.returns.obj);
					if (!retKot.equals(""))
						sw.append(")").append(": ").append(retKot).append("\n");
					else
						sw.append(")").append("\n");
				}
				else
					sw.append(")").append("\n");
				sw.append("   {").append("\n");
				String returnKot = null;
				boolean funReturns = (fd.returns != null && !fd.returns.objOriginal.equals("LuaInterface") && !(returnKot = getKotlinObject(fd.returns.objOriginal, fd.returns.obj)).equals(""));
				if(funReturns)
				{
					if(isKotlinPrimitive(returnKot, null))
					{
						sw.append("       return ").append(moduleCamel).append("?.").append(fd.name).append("(");
					}
					else
					{
						sw.append("       val pobj = ").append(fd.returns.name.trim()).append("()").append("\n");
						sw.append("       val obj = ").append(moduleCamel).append("?.").append(fd.name).append("(");
					}
				}
				else
					sw.append("       ").append(moduleCamel).append("?.").append(fd.name).append("(");
				for (int i = 0; i < fd.parameters.size(); i++)
				{
					FunctionParDef fdp = fd.parameters.get(i);
					String parName = fdp.name;
					if (parName.equals("var") || parName.equals("val"))
						parName = "v";
					sw.append(parName);
					String kType = getKotlinObject(fdp.objOriginal, fdp.obj);
					if(!isKotlinPrimitive(kType, null))
						sw.append("?.GetNativeObject()");
					if (i != fd.parameters.size() - 1)
					{
						sw.append(", ");
					}
				}
				sw.append(")").append("\n");
				if(funReturns)
				{
					if(!isKotlinPrimitive(returnKot, null))
					{
						sw.append("       pobj.SetNativeObject(obj)").append("\n");
						sw.append("       return pobj").append("\n");
					}
				}
				sw.append("   }").append("\n");
			}
			sw.append("   ").append(baseExists ? "override " : "").append("open fun GetNativeObject(): ").append(pack).append("?").append("\n");
			sw.append("   {").append("\n");
			sw.append("       return ").append(moduleCamel).append("\n");
			sw.append("   }").append("\n");
			sw.append("   fun SetNativeObject(par :").append(pack).append("?)").append("\n");
			sw.append("   {").append("\n");
			sw.append("       ").append(moduleCamel).append(" = par").append("\n");
			sw.append("   }").append("\n");
			sw.append("}");
			androidMap.put(module, sw.toString());
		}
    }

    public static String getKotlinObject(String obj, String objN)
	{
		String objFix = obj.replace("final", "").trim();
		String objNFix = null;
		if(objN != null)
			objNFix = objN.replace("final", "").trim();
		switch (objFix)
		{
		case "byte":
			return "Byte";
		case "short":
			return "Short";
		case "int":
			return "Int";
		case "long":
			return "Long";
		case "char":
			return "Char";
		case "float":
			return "Float";
		case "double":
			return "Double";
		case "boolean":
			return "Boolean";
		case "Byte":
			return "Byte?";
		case "Short":
			return "Short?";
		case "Integer":
			return "Int?";
		case "Long":
			return "Long?";
		case "Char":
			return "Char?";
		case "Float":
			return "Float?";
		case "Double":
			return "Double?";
		case "Boolean":
			return "Boolean?";
		case "Object":
			return "Any?";
		case "String":
			return "String?";
		case "void":
			return "";
		default:
			{
				if(objNFix != null)
				{
					if(objNFix.equals("void"))
						return "";
					else if(!objNFix.equals(""))
						return getKotlinObject(objNFix, null);
					else
						return "";
				}
				return objFix + "?";
			}
		}
	}

	public static boolean isKotlinPrimitive(String obj, String objN)
	{
		String objFix = obj.replace("final", "").trim();
		String objNFix = null;
		if(objN != null)
			objNFix = objN.replace("final", "").trim();
		switch (objFix)
		{
		case "byte":
		case "short":
		case "int":
		case "long":
		case "char":
		case "float":
		case "double":
		case "boolean":
		case "Byte":
		case "Short":
		case "Integer":
		case "Long":
		case "Char":
		case "Float":
		case "Double":
		case "Boolean":
		case "String":
		case "String?":
		case "Any":
		case "Any?":
		case "Int":
		case "Byte?":
		case "Short?":
		case "Int?":
		case "Long?":
		case "Char?":
		case "Float?":
		case "Double?":
		case "Boolean?":
			return true;
		default:
			return false;
		}
	}

	/**
	 * (Ignore)
	 */
	public static void ResourceLoader(String path, boolean kotlin, String clsname) throws Exception
	{
        /*path = "D:\\MobileWorkspace\\StudioWorkspace\\topping-kotlin-sample\\androidApp\\build\\intermediates\\compile_and_runtime_not_namespaced_r_class_jar\\debug\\R.jar";
        clsname = "dev.topping.kotlin.androidApp.R";
        kotlin = true;*/

		URLClassLoader child = new URLClassLoader(
                new URL[] { new File(path).toURI().toURL() },
                DocumentParser.class.getClassLoader()
        );

        Class rClass = Class.forName(clsname, true, child);

        final String _TEMPLATE;
        if(kotlin)
		{
			_TEMPLATE = "package dev.topping.kotlin\n" +
					"\n" +
					"class LR {\n" +
					"%s\n" +
					"}";
		}
        else
		{
			_TEMPLATE = "-------------------------------------------------------------------------------\n" +
					"\n" +
					"---@class LR\n" +
					"local LR = Class()\n" +
					"\n" +
					"%s\n" +
					"\n" +
					"_G['LR'] = LR\n" +
					"return LR";
		}

		if(rClass == null)
			return;

		Class<?>[] declaredClasses = rClass.getDeclaredClasses();

		StringBuilder sb = new StringBuilder();
		HashSet<String> reservedKeywordSet = new HashSet<>();
		reservedKeywordSet.add("and");
		reservedKeywordSet.add("end");
		reservedKeywordSet.add("in");
		reservedKeywordSet.add("repeat");
		reservedKeywordSet.add("break");
		reservedKeywordSet.add("do");
		reservedKeywordSet.add("else");
		reservedKeywordSet.add("false");
		reservedKeywordSet.add("for");
		reservedKeywordSet.add("function");
		reservedKeywordSet.add("elseif");
		reservedKeywordSet.add("if");
		reservedKeywordSet.add("not");
		reservedKeywordSet.add("local");
		reservedKeywordSet.add("nil");
		reservedKeywordSet.add("or");
		reservedKeywordSet.add("return");
		reservedKeywordSet.add("then");
		reservedKeywordSet.add("true");
		reservedKeywordSet.add("until");
		reservedKeywordSet.add("while");

		for (int d = 0; d < declaredClasses.length; d++)
		{
			Field[] declaredFields = declaredClasses[d].getDeclaredFields();

			StringBuilder innerBuilder = new StringBuilder();
			/**
			 * -------------------------------------------------------------------------------
			 * ---@class LR
			 * local LR = {}
			 *
			 * ---@class strings
			 * local strings = {
			 *     test = "",
			 *     asd = ""
			 * }
			 *
			 * LR.strings = strings
			 *
			 * ---@class attr
			 * local attr = {
			 *     bebe = "",
			 *     ccc = ""
			 * }
			 *
			 * LR.attr = attr
			 *
			 * _G['LR'] = LR
			 * return LR
			 */
			/*
			class layout {
				companion object {
					val activity_main:LuaRef = dev.topping.kotlin.LuaRef()
				}
			}
			*/
			boolean applyHeader = true;
			int count = 0;
			int loopCount = 0;
			int loopMax = 900;
			for (int i = 0; i < declaredFields.length; i++)
			{
				if(applyHeader) {
					applyHeader = false;
					if (kotlin) {
						if(count == 0)
							innerBuilder.append("class ").append(declaredClasses[d].getSimpleName()).append(" {").append("\n");
						else
							innerBuilder.append("class ").append(declaredClasses[d].getSimpleName()).append(count).append(" {").append("\n");
						innerBuilder.append("    companion object {").append("\n");
					} else {
						innerBuilder.append("---@class ").append(declaredClasses[d].getSimpleName()).append("\n");
						innerBuilder.append("local ").append(declaredClasses[d].getSimpleName()).append(" = {").append("\n");
					}
				}
				String name = declaredFields[i].getName();
				//ALT=2131165184,CTRL=2131165185,FUNCTION=2131165186,META=2131165187,SHIFT=2131165188,SYM=2131165189
				if(name.equalsIgnoreCase("ALT")
						|| name.equalsIgnoreCase("CTRL")
						|| name.equalsIgnoreCase("FUNCTION")
						|| name.equalsIgnoreCase("META")
						|| name.equalsIgnoreCase("SHIFT")
						|| name.equalsIgnoreCase("SYM"))
					continue;

				if(reservedKeywordSet.contains(name))
					name = name.toUpperCase(Locale.US);

				Object o = null;
				try
				{
					o = declaredFields[i].get(null);
				}
				catch (IllegalAccessException e)
				{

				}

				Type type = declaredFields[i].getType();

				if(kotlin)
				{
					//LuaRef.WithValue(").append(o).append(")");
					if (type instanceof Class && ((Class) type).isArray())
					{
						int len = Array.getLength(o);
						if (len == 0)
							continue;
						innerBuilder.append("        val ").append(name).append(" = arrayOf(");
						for (int k = 0; k < len; ++k)
						{
							innerBuilder.append("LuaRef.WithValue(\"").append(name).append("\", ").append(Array.get(o, k)).append(")");
							if (k + 1 < len)
							{
								innerBuilder.append(",");
							}
						}
						innerBuilder.append(")");
					}
					else
					{
						innerBuilder.append("        val ").append(name).append(" = ");
						innerBuilder.append("LuaRef.WithValue(\"@").append(declaredClasses[d].getSimpleName()).append("/").append(name).append("\", ").append(o).append(")");
					}
				}
				else
				{
					innerBuilder.append("---@type LuaRef").append("\n");
					if (type instanceof Class && ((Class) type).isArray())
					{
						int len = Array.getLength(o);
						if (len == 0)
							continue;

						innerBuilder.append(name).append("=");
						innerBuilder.append("{[0] = ");
						for (int k = 0; k < len; ++k)
						{
							innerBuilder.append(Array.get(o, k));
							if (k + 1 < len)
							{
								innerBuilder.append(",");
							}
						}
						innerBuilder.append("}");
					}
					else
					{
						innerBuilder.append(name).append("=");
						innerBuilder.append(o);
					}

					if (i + 1 < declaredFields.length)
					{
						innerBuilder.append(",");
					}
				}
				innerBuilder.append("\n");
				if(kotlin) {
					loopCount++;
					if (loopCount >= loopMax) {
						count++;
						loopCount = 0;
						applyHeader = true;
						innerBuilder.append("    }").append("\n").append("}").append("\n\n");
					}
				}
			}
			if(kotlin)
				innerBuilder.append("    }").append("\n").append("}").append("\n\n");
			else
				innerBuilder.append("\n}\n").append("LR.").append(declaredClasses[d].getSimpleName()).append(" = ").append(declaredClasses[d].getSimpleName()).append("\n\n");
			sb.append(innerBuilder);
		}

		String formatted = String.format(_TEMPLATE, sb.toString());
		FileOutputStream fs = null;

		File fOutputFolder = new File(outputFolder);
		if(fOutputFolder.exists())
			deleteDirectory(fOutputFolder);
		fOutputFolder.mkdir();

		try
		{
			fs = new FileOutputStream(outputFolder + "/LR" + (!kotlin ? ext : ".kt"));
			fs.write(formatted.getBytes());
			fs.flush();
			fs.close();
		}
		catch (Exception e)
		{

		}
	}
}
