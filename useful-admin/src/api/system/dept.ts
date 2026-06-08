import {request} from '@/utils/request';
import {Dept} from '@/api/model/deptModel'

export const DeptApi = {
  getDeptList() {
    return request.get({
      url: `/system/dept/list`
    })
  }
}

