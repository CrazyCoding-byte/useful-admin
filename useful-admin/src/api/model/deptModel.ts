export interface Dept {
  deptId?: Number;
  parentId?: Number;
  ancestors?: string;
  deptName?: string;
  leader?: String;
  children?: Dept[]
}


