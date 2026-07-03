import os
import traceback
import requests
from importlib.machinery import SourceFileLoader
import json

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
}


def spider(cache, api):
    name = os.path.basename(api.split('?', 1)[0])
    path = cache + '/' + name
    download(path, api)
    name = name.split('.')[0]
    return SourceFileLoader(name, path).load_module().Spider()


def candidate_urls(api):
    urls = []
    if api:
        urls.append(api)
        if 'gh-proxy.com/' in api:
            urls.append(api.split('gh-proxy.com/', 1)[1])
        if 'ghproxy.com/' in api:
            urls.append(api.split('ghproxy.com/', 1)[1])
        if 'raw.githubusercontent.com' in api and api not in urls:
            urls.append(api)
    seen = set()
    result = []
    for url in urls:
        if url and url not in seen:
            seen.add(url)
            result.append(url)
    return result


def download(path, api):
    if not api.startswith('http'):
        writeFile(path, str.encode(api))
        return
    last_error = None
    for url in candidate_urls(api):
        try:
            writeFile(path, redirect(url).content)
            return
        except Exception as e:
            last_error = e
    raise Exception('download failed: %s' % last_error)


def writeFile(path, content):
    parent = os.path.dirname(path)
    if parent and not os.path.exists(parent):
        os.makedirs(parent)
    with open(path, 'wb') as f:
        f.write(content)


def redirect(url):
    rsp = requests.get(url, allow_redirects=False, verify=False, timeout=20, headers=HEADERS)
    if rsp.status_code in (301, 302, 303, 307, 308) and 'Location' in rsp.headers:
        return redirect(rsp.headers['Location'])
    rsp.raise_for_status()
    return rsp


def str2json(content):
    if not content:
        return {}
    return json.loads(content)


def invoke(ru, name, default, *args):
    fn = getattr(ru, name, None)
    if fn is None:
        return default
    arg_lists = [args]
    if len(args) > 0:
        arg_lists.append(args[:-1])
    if len(args) > 1:
        arg_lists.append(args[:-2])
    if len(args) > 2:
        arg_lists.append(args[:-3])
    arg_lists.append(())
    seen = set()
    for call_args in arg_lists:
        key = len(call_args)
        if key in seen:
            continue
        seen.add(key)
        try:
            return fn(*call_args)
        except TypeError:
            continue
        except Exception:
            traceback.print_exc()
            return default
    return default


def dumps_result(result, default):
    if result is None:
        result = default
    return json.dumps(result, ensure_ascii=False)


def getDependence(ru):
    return ru.getDependence()


def init(ru, extend):
    ru.init(extend)


def homeContent(ru, filter):
    return dumps_result(invoke(ru, 'homeContent', {'class': []}, filter), {'class': []})


def homeVideoContent(ru):
    return dumps_result(invoke(ru, 'homeVideoContent', {'list': []}), {'list': []})


def categoryContent(ru, tid, pg, filter, extend):
    return dumps_result(
        invoke(ru, 'categoryContent', {'list': []}, tid, pg, filter, str2json(extend)),
        {'list': []}
    )


def detailContent(ru, array):
    return dumps_result(invoke(ru, 'detailContent', {'list': []}, str2json(array)), {'list': []})


def searchContent(ru, key, quick, pg="1"):
    # 多数 Pyramid 脚本 searchContent 只有 (key, quick) 两参，传 pg 会 TypeError
    return dumps_result(
        invoke(ru, 'searchContent', {'list': []}, key, quick, pg),
        {'list': []}
    )


def playerContent(ru, flag, id, vipFlags):
    return dumps_result(
        invoke(ru, 'playerContent', {'parse': 1, 'url': id}, flag, id, str2json(vipFlags)),
        {'parse': 1, 'url': id}
    )


def liveContent(ru, url):
    return invoke(ru, 'liveContent', '', url)


def localProxy(ru, param):
    return invoke(ru, 'localProxy', None, str2json(param))


def action(ru, action):
    return dumps_result(invoke(ru, 'action', {}, action), {})


def destroy(ru):
    invoke(ru, 'destroy', None)


def manualVideoCheck(ru):
    result = invoke(ru, 'manualVideoCheck', False)
    return bool(result)


def isVideoFormat(ru, url):
    result = invoke(ru, 'isVideoFormat', False, url)
    return bool(result)
